package de.grinder.android_fi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.grinder.android_fi.ProcessUtils.ProcUtilException;
import de.grinder.android_fi.experimentResultDetection.InputStreamCapturer;

/**
 * Controls one instance of the Android emulator.
 * 
 * This class represents an Android emulator instance, in running as well as in not
 * running state. The emulator is program-wide identified by its control and ADB ports.
 * The ports should be unique for all running emulator processes, regardless whether they
 * are started using an instance of this class or by some external means. This is required
 * to allow reliable control of the emulator, esp., when using ADB. The control port can
 * be freely chosen at instantiation time. The ADB port, however, is auto-computed to be
 * control port plus one, e.g., if the selected control port is `42`, the ADB port is
 * computed to be {@code 43} Note that the emulator usually uses the same scheme for
 * assigning ports. However, its range for control ports is limited to ports between
 * {@code 5554} and {@code 5584} and the control ports must be even, i.e., {@code 5554},
 * {@code 5556}, {@code 5558}, etc.
 * <p>
 * Each instance of this class controls its own emulator process and
 * {@link InputStreamCapturer} instance. Moreover, an ADB wrapper can be obtained from
 * this class that allows to control the emulator if it is running. All {@link Emulator}
 * settings and configuration parameters are encapsulated in an
 * {@link EmulatorConfiguration} objects that must not be shared between different
 * {@link Emulator} instances.
 * <p>
 * Note that the emulator process must be exclusively controlled by its instance of this
 * class to ensure proper operation, esp. with regard to starting and stopping. Emulator
 * processes associated with an instance of this class must not be terminated by external
 * means. ADB access to the started emulator should be limited to the provided ADB
 * wrapper.
 * <p>
 * Before the emulator process is started, all individual image files are overwritten with
 * their vanilla versions (see {@link #resetImages()}). This includes the userdata and the
 * SD card image, but not the system image, which is copied by the emulator itself. Note
 * that the {@code -wipe-data} option is not used to perform a userdata image reset as
 * this is done explicitly before starting the emulator. In future versions this may also
 * apply to the system image. The Android emulator creates a copy of the used system image
 * per emulator run. This temporary system image map file is created with a random name
 * somewhere in {@code /tmp}. In order to prevent file system trashing over multiple
 * emulator runs, the map file is identified by searching the emulator process output (see
 * {@link #startInitScanner()}) and deleted during the emulator shutdown process (see
 * {@link #cleanup()}).
 * <p>
 * Generally, {@link Emulator} instances should only be constructed by the
 * {@link EmulatorFactory}. This ensures unique and consistent emulator ID and port
 * assignment.
 * <p>
 * When starting multiple emulator processes off the same Android Virtual Device (AVD),
 * the emulator detects this and may write configuration changes to temporary files, which
 * are created similar to the system image map file. This prevents conflicts with AVD
 * configuration files, but has no undesirable side-effects for using this class since all
 * parameters that are individual for emulator runs are specified as emulator startup
 * parameters. Note, however, that the (small) temporary configuration files are not
 * cleaned up together with the (large) system image map files.
 * <p>
 * This class is thread safe, i.e., it should be safe to invoke its methods from multiple
 * threads.
 */
public class Emulator {

  /**
   * Starting of accounting system output line that states the PID of the started emulator
   * process. This constant is used by the init data scanner (see
   * {@link Emulator#initDataScanner}) in order to identify the current emulator PID.
   * */
  private static final String EMULATOR_PID_LINE = "Child pid: ";

  /** The period in milliseconds for executing the init data scanner. */
  private static final long INIT_SCANNER_PERIOD = 250l;

  /**
   * Starting of emulator output line that states the used temporary system image map
   * file. This constant is used by the init data scanner (see
   * {@link Emulator#initDataScanner}) in order to identify the current map file.
   */
  private static final String SYSTEM_IMAGE_MAP_LINE = "emulator: mapping 'system' NAND image to ";

  /** Regex pattern that matches a valid accounting data set as produced by getdelays. */
  private static final String VALID_ACC_DATASET_PATTERN = "\\((\\w+: \\d+;)+\\)";

  private List<String> accountingData = null;

  /** Process object for running accounting or {@code null} if accounting is not running. */
  private Process accProcess = null;

  /**
   * The ADB wrapper instance that is attached to this emulator or {@code null} if the ADB
   * wrapper was never queried by {@link #getAdb()}. Note that the ADB wrapper can exist
   * Independent of whether an emulator process is running.
   */
  private AndroidDebuggingBridge adb;

  /** Emulator output capturer (stdout). */
  private final InputStreamCapturer emuCapturer;

  /** Emulator settings and configuration. */
  private final EmulatorConfiguration emuConfig;

  /** PID of the emulator process or {@code 0} if no emulator is running. */
  private int emuPid = 0;
  /**
   * Current initial data scanner while it is running or {@code null} if no scanner is
   * running.
   */
  private ScheduledFuture<?> initDataScanner;

  /** Instance level logger */
  private final Logger LOGGER;

  /**
   * Executor for the init data scanner. Using a executor here may be a little overdone,
   * but is is so much nicer than using a raw thread.
   */
  private final ScheduledExecutorService scheduler;

  private boolean shutdownComplete = true;

  private final Object shutdownLock = new Object();

  /**
   * Path of the current temporary system image map file or {@code null} if there is no
   * current map file or it is unknown.
   */
  private String sysImageMapFile;

  /**
   * Constructs an {@link Emulator} object from the specified arguments.
   * 
   * @param settings
   *          The {@link EmulatorConfiguration} object that contains the configuration for
   *          this {@link Emulator} instance.
   */
  public Emulator(final EmulatorConfiguration settings) {
    final String logId = settings.getLogId();
    LOGGER = LoggerFactory.getLogger(String.format("%s<%s>", Emulator.class.getName(),
        logId));
    emuConfig = settings;
    emuCapturer = new InputStreamCapturer(logId);
    scheduler = Executors.newSingleThreadScheduledExecutor();
  }

  /**
   * Delete temporary files that are created during an emulator run.
   * 
   * Currently, only the temporary system image map file is deleted. In the future,
   * however, additional files, e.g., lock files, may also be deleted. Note that this
   * method does not cleanup the temporary configuration files that are created by the
   * Android emulator if multiple emulator processes are started off the same AVD.
   */
  private void cleanup() {
    LOGGER.debug("Performing emulator cleanup");
    if (initDataScanner != null && !initDataScanner.isDone()) {
      final boolean ret = initDataScanner.cancel(true);
      initDataScanner = null;
      LOGGER.warn(lm("System image map file scanner was not done on cleanup. "
          + "Canceled now explicitly: %b", ret));
    }
    if (sysImageMapFile != null) {
      try {
        Files.delete(Paths.get(sysImageMapFile));
        LOGGER.debug(lm("Deleted temporary system image map file [%s]", sysImageMapFile));
      } catch (final Exception e) {
        LOGGER.warn(lm("Failed to delete temporary system image map file [%s]: %s (%s)",
            sysImageMapFile, e.getClass().getName(), e.getMessage()));
      }
      sysImageMapFile = null;
    } else {
      LOGGER.warn("Cannot remove temporary system image map file. "
          + "Unknown file location");
    }
  }

  private void collectAccountingStats() {
    final String rawOutput = emuCapturer.getOutput();
    final Matcher startMatcher = Pattern.compile(String.format("PID\\s+%d\n", emuPid))
        .matcher(rawOutput);
    if (startMatcher.find()) {
      final String[] accLines = rawOutput.substring(startMatcher.end()).split("\n");
      final List<String> accDataSets = new ArrayList<>();
      for (final String line : accLines) {
        if (line.matches(VALID_ACC_DATASET_PATTERN)) {
          accDataSets.add(line);
          LOGGER.debug(lm("Found valid acc data set: %s", line));
        } else {
          LOGGER.warn(lm("Saw invalid acc data line: %s", line));
        }
      }
      if (accDataSets.isEmpty()) {
        final String msg = lm("Failed to find valid accounting data in emulator output");
        LOGGER.error(msg);
      } else {
        accountingData = accDataSets;
        LOGGER.debug(lm("Found %d accounting data sets", accDataSets.size()));
      }
    } else {
      final String msg = lm("Failed to find accounting stats start in emulator output");
      LOGGER.error(msg);
    }
  }

  /**
   * Convenience wrapper for {@link String#format(String, Object...)} intended for log
   * message formatting.
   * 
   * @param format
   *          Format string to use for formatting.
   * @param args
   *          Arguments to format.
   * @return The formatted string.
   */
  private String lm(final String format, final Object... args) {
    return String.format(format, args);
  }

  /**
   * Reset the SD card and userdata images to their vanilla versions.
   * 
   * The reset is performed by copying the vanilla images over the image files that are
   * used by this emulator.
   * 
   * @throws IOException
   *           If one or more vanilla images could not be copied.
   */
  private void resetImages() throws IOException {
    LOGGER.debug("Resetting system, userdata and SD card images");

    // manual system image reset in future work as it requires changes to the Android
    // emulator
    // LOGGER.debug("Copying [{}] to [{}]", emuSettings.getSystemVanillaImage(),
    // emuSettings.getSystemImage());
    // Files.copy(Paths.get(emuSettings.getSystemVanillaImage()),
    // Paths.get(emuSettings.getSystemImage()), StandardCopyOption.REPLACE_EXISTING);

    LOGGER.debug(lm("Copying [%s] to [%s]", emuConfig.getUserdataVanillaImage(),
        emuConfig.getUserdataImage()));
    Files.copy(Paths.get(emuConfig.getUserdataVanillaImage()),
        Paths.get(emuConfig.getUserdataImage()), StandardCopyOption.REPLACE_EXISTING);

    LOGGER.debug(lm("Copying [%s] to [%s]", emuConfig.getSdCardVanillaImage(),
        emuConfig.getSdCardImage()));
    Files.copy(Paths.get(emuConfig.getSdCardVanillaImage()),
        Paths.get(emuConfig.getSdCardImage()), StandardCopyOption.REPLACE_EXISTING);
  }

  private void resetShutdownState() {
    synchronized (shutdownLock) {
      shutdownComplete = false;
    }
  }

  private void signalShutdownComplete() {
    synchronized (shutdownLock) {
      shutdownComplete = true;
      shutdownLock.notifyAll();
    }
  }

  /**
   * Starts the data scanner for identifying needed data in the startup output of the
   * emulator and accounting system.
   * 
   * The scanner identifies the temporary mapping file for the system image and emulator
   * PID. The scanner uses a separate thread to periodically search the accounting and
   * emulator process output. The scanner is terminated as soon as it has identified what
   * it is looking for or it executed too often/long.
   */
  private void startInitScanner() {
    LOGGER.debug("Starting init data scanner");
    initDataScanner = scheduler.scheduleWithFixedDelay(new Runnable() {

//      private int count = 0;

      private boolean foundEmuPid = false;
      private boolean foundSysImg = false;
      // scan at max for three minutes after PID was seen
//      private final int MAX_COUNT = (int) ((180l * 1000l) / INIT_SCANNER_PERIOD);
      private final String MISSED_MARKER_LINE = "Kernel command line:";

//      {
//        LOGGER.debug(String.format("Init data scanner max: %d", MAX_COUNT));
//      }

      private void cancelScanner() {
        if (initDataScanner != null) {
          initDataScanner.cancel(false);
          initDataScanner = null;
        }
      }

      private String getRestOfLine(final String buf, final String lineStart) {
        final int startIdx = buf.indexOf(lineStart);
        if (startIdx >= 0) {
          final int endIdx = buf.indexOf("\n", startIdx);
          if (endIdx > startIdx) {
            final String line = buf.substring(startIdx, endIdx);
            final String rest = line.substring(lineStart.length());
            return rest;
          } else {
            LOGGER.warn(lm("Init data scanner found line start but not end for [%s]",
                lineStart));
          }
        }
        return null;
      }

      @Override
      public void run() {
        // using simple string operations here over regex in hope for better performance
        final String buf = emuCapturer.getOutput();
        if (!foundEmuPid) {
          final String dat = getRestOfLine(buf, EMULATOR_PID_LINE);
          if (dat != null) {
            emuPid = Integer.parseInt(dat);
            foundEmuPid = true;
            LOGGER.debug(lm("Init data scanner found emulator PID: %d", emuPid));
          }
        }
        if (!foundSysImg) {
          final String dat = getRestOfLine(buf, SYSTEM_IMAGE_MAP_LINE);
          if (dat != null) {
            sysImageMapFile = dat;
            foundSysImg = true;
            LOGGER.debug(lm("Init data scanner found system image map file: %s",
                sysImageMapFile));
          }
        }
        if (foundEmuPid && foundSysImg) {
          cancelScanner();
        } else if (buf.contains(MISSED_MARKER_LINE)) {
          LOGGER.error("Canceling init data scanner without complete data after "
              + "reaching marker line.");
          cancelScanner();
        }
//        else if ((foundEmuPid || foundSysImg) && ++count >= MAX_COUNT) {
//          // idea is that after one is found the other should also be seen soon.
//          // if not, assume we missed it and stop after some time
//          // it turns out that this is a bad idea for high stress experiments because
//          // the emulator is just soooo slow
//          LOGGER.warn(String.format(
//              "Canceling init data scanner without complete data after %d scans.", count));
//          cancelScanner();
//        }

        // this should not happen due to the order of outputs
        if (!foundEmuPid && foundSysImg) {
          LOGGER
              .warn("Init data scanner found sys image, but not emulator PID. Strange!");
        }
      }
    }, 0, INIT_SCANNER_PERIOD, TimeUnit.MILLISECONDS);
  }

  /**
   * Stops the capturing of the emulator process output.
   */
  private void stopEmuCapture() {
    try {
      emuCapturer.stopCapturing();
    } catch (final IOException e) {
      LOGGER.warn(lm("Error stopping emulator output capturing: %s", e.getMessage()));
    }
  }

  public int awaitEmuTermination() throws EmulatorException {
    if (!hasAccProcess()) {
      throw new EmulatorException("Cannot wait for emulator termination. No process.");
    }

    try {
      final int result = accProcess.waitFor();
      return result;
    } catch (final Exception e) {
      LOGGER.error(lm("Failed to wait for emulator termination. %s", e.getMessage()), e);
      throw new EmulatorException("Interrupted while waiting for process.", e);
    }
  }

  public int awaitEmuTerminationSilent() throws EmulatorException {
    if (!hasAccProcess()) {
      throw new EmulatorException("Cannot wait for emulator termination. No process.");
    }

    try {
      final int result = accProcess.waitFor();
      return result;
    } catch (final Exception e) {
      throw new EmulatorException("Interrupted while waiting for process.", e);
    }
  }

  public void awaitShutdown() throws EmulatorException {
    LOGGER.info("Awaiting emulator shutdown.");
    synchronized (shutdownLock) {
      try {
        while (!shutdownComplete) {
          shutdownLock.wait();
        }
        LOGGER.debug("Awaited emulator shutdown.");
      } catch (final InterruptedException e) {
        LOGGER.error("Failed to await terminator shutdown.");
        throw new EmulatorException("Interrupted while waiting for emulator shutdown.", e);
      }
    }
  }

  public List<String> getAccountingStats() {
    return accountingData;
  }

  /**
   * Retrieves the ADB wrapper instance for this emulator.
   * 
   * Each emulator instance has one associated ADB wrapper instance. The ADB wrapper is
   * unaware of the current emulator process state, i.e., its methods can always be
   * invoked, but they usually return errors if the emulator process is not running.
   * 
   * @return The ADB wrapper instance for this emulator.
   */
  public AndroidDebuggingBridge getAdb() {
    if (adb == null) {
      adb = new AndroidDebuggingBridge(emuConfig.getSettings(), emuConfig.getAdbName(),
          emuConfig.getLogId());
    }
    return adb;
  }

  /**
   * Retrieves the settings and configuration parameter object for the emulator instance.
   * 
   * @return Emulator configuration object.
   */
  public EmulatorConfiguration getConfig() {
    return emuConfig;
  }

  /**
   * Retrieve the output capturer that captures the console output of the running emulator
   * process.
   * 
   * The output capturer is unaware of the current emulator process state. Output is only
   * captured if the process is running.
   * 
   * @return The emulator output capturer.
   */
  public InputStreamCapturer getEmulatorOutputCapturer() {
    return emuCapturer;
  }

  /**
   * Checks whether a process is currently associated with the {@link Emulator} instance.
   * 
   * Note that the process may not be running.
   * 
   * @return {@code True} if a process is associated, {@code false} otherwise.
   */
  public boolean hasAccProcess() {
    return accProcess != null;
  }

  /**
   * Checks whether a running emulator process is associated to this {@link Emulator}
   * instance.
   * 
   * @return {@code True} if the emulator is running, {@code false} otherwise.
   */
  public synchronized boolean isAccRunning() {
    return hasAccProcess() && ProcessUtils.isRunning(accProcess);
  }

  public synchronized boolean isEmuRunning() {
    if (emuPid != 0) {
      try {
        return ProcessUtils.isRunning(emuPid);
      } catch (final ProcUtilException e) {
        // stay silent, consider as not running
      }
    }
    return false;
  }

  /**
   * Kills the process of this Android emulator.
   * 
   * The running emulator process that is controlled by this instance is forcibly killed
   * using {@code SIG_KILL}. Moreover, the output capturing is stopped and a cleanup is
   * performed. Note that this method is more or less un-interruptible.
   * 
   * @throws EmulatorException
   *           If the associated emulator process was probably not killed.
   */
  public synchronized void kill() throws EmulatorException {
    LOGGER.info("Killing emulator");
    if (accProcess != null && emuPid != 0) {
      // NOTE: accounting process must be destroyed prior to stopping the stream capturing
      // in order to prevent deadlock (Reader.readline block issue) and to capture
      // accounting stats.
      try {
        // kill hard to be on the safe side
        ProcessUtils.kill(emuPid, ProcessUtils.SIG_KILL);
        while (true) {
          try {
            accProcess.waitFor();
            break;
          } catch (final InterruptedException e) {
            // ignore interrupt, we want to have a clean shutdown
          }
        }
        accProcess = null;
        LOGGER.debug("Emulator (and accounting) killed successfully");
        stopEmuCapture();
        collectAccountingStats();
        emuPid = 0;
        cleanup();
      } catch (final ProcUtilException e) {
        final String msg = lm("Failed to kill emulator: %s", e.getMessage());
        LOGGER.error(msg);
        throw new EmulatorException(msg, e);
      } finally {
        signalShutdownComplete();
      }
    } else {
      LOGGER.warn("Skipped emulator kill: no associated emulator process");
    }
    if (accProcess == null && emuPid != 0 || accProcess != null && emuPid == 0) {
      LOGGER.error(lm(
          "Inconsistent emulator state: accounting process <%s>, emu PID <%d>",
          accProcess, emuPid));
    }
  }

  /**
   * Starts a new emulator process.
   * 
   * A new emulator process is only started if there is no already running process that is
   * controlled by this instance. Note that this method does not recognize conflicts
   * between running emulator processes, i.e., if there is an emulator running on the same
   * ports as the emulator that will be started here, this is not detected. After the
   * emulator process started successfully, the console output capturer and the system
   * image map file scanner are started.
   * 
   * @throws EmulatorException
   *           If the emulator process was not started, e.g., because a process was
   *           already running or some process start error.
   */
  public synchronized void start() throws EmulatorException {
    LOGGER.info("Starting Android emulator");

    if (accProcess != null) {
      final String msg = lm("Cannot start emulator; it is already running.");
      LOGGER.error(msg);
      throw new EmulatorException(msg);
    }

    try {
      resetShutdownState();
      resetImages();
      accountingData = null;
      LOGGER.info(Arrays.toString(emuConfig.getAccountingCommand()));
      accProcess = ProcessUtils.startProcessPipe(emuConfig.getAccountingCommand());
      emuCapturer.startCapturing(accProcess.getInputStream());
      startInitScanner();
      LOGGER.info("Emulator is booting");
    } catch (IOException | ProcUtilException e) {
      LOGGER.error(lm("Failed to start Android Emulator: %s", e.getMessage()));
      if (accProcess != null) {
        accProcess.destroy();
        accProcess = null;
      }
      throw new EmulatorException("Android emulator start failed: " + e.getMessage(), e);
    }
  }

  /**
   * This class encapsulates {@link Emulator} specific settings and configuration
   * parameters.
   * 
   * Note that some of the parameters are global for all {@link Emulator} instances and
   * some are individual for each {@link Emulator} instance.
   * 
   * TODO: full documentation
   */
  public static class EmulatorConfiguration {

    /**
     * Getdelays command used for collecting emulator account data. This is the complete
     * command including the emulator command.
     */
    private final String[] accCommand;

    /** Emulator name in ADB. Format is {@code "emulator-<controlPort>"}. */
    private final String adbName;

    /** Emulator ADB port. Auto-computed to be {@code controlPort + 1}. */
    private final int adbPort;

    private final String avdName;

    /** Emulator control port. Can be freely chosen. */
    private final int controlPort;

    /** Emulator command including executable name and all required arguments. */
    private final String[] emuCommand;

    /**
     * Unique emulator ID which identifies every {@link Emulator} instance that exists in
     * the program.
     */
    private final int emuId;

    /** Central experiment settings used as basis of emulator settings. */
    private final ExperimentSettings expSettings;

    /** Path to the SD card image, individual for every {@link Emulator} instance. */
    private final String sdCardImage;

    /** Path to the system image, individual for every {@link Emulator} instance. */
    private final String systemImage;

    /** Path to the SD card image, individual for every {@link Emulator} instance. */
    private final String userdataImage;

    private static String[] mergeCommands(final String[]... cmdArr) {
      int mergedLen = 0;
      for (final String[] cmds : cmdArr) {
        mergedLen += cmds.length;
      }
      final String[] merged = new String[mergedLen];
      int offset = 0;
      for (final String[] cmds : cmdArr) {
        System.arraycopy(cmds, 0, merged, offset, cmds.length);
        offset += cmds.length;
      }
      return merged;
    }

    /**
     * Constructs an {@link EmulatorConfiguration} object from the specified arguments.
     * 
     * @param id
     *          Emulator ID. Must be unique for the current program run.
     * @param controlPort
     *          Android emulator control port. Must be unique for the current program run.
     * @param expSettings
     *          Central {@link ExperimentSettings} object which contains all global
     *          experiment settings.
     */
    public EmulatorConfiguration(final int id, final int controlPort,
        final ExperimentSettings expSettings) {
      emuId = id;
      this.controlPort = controlPort;
      this.expSettings = expSettings;

      adbPort = controlPort + 1;
      adbName = String.format("emulator-%d", controlPort);
      avdName = String.format("%s-%d", expSettings.AVD_NAME, emuId);
      systemImage = prefixFilePath(expSettings.SYSTEM_IMAGE);
      sdCardImage = prefixFilePath(expSettings.SDCARD_IMAGE);
      userdataImage = prefixFilePath(expSettings.USERDATA_IMAGE);

      // @formatter:off
      emuCommand = new String[]{ expSettings.EMULATOR,
          "-avd", getAvdName(),
          "-kernel", expSettings.KERNEL_BIMAGE,
          "-system", getSystemVanillaImage(),
          "-sdcard", getSdCardImage(),
          "-data", getUserdataImage(),
          "-initdata", getUserdataVanillaImage(),
          "-ports", String.format("%d,%d", controlPort, adbPort),
          "-show-kernel", "-verbose",
          "-no-snapshot-save", "-no-boot-anim", "-no-window", "-no-audio", "-no-cache",
          
          // the following parameters allow to write to the system image, but the NAND
          // mapping file is still created, so we don't use this option for now. 
          // "-qemu", "-nand",
          // String.format("system,size=0x%s,file=%s,pagesize=512,extrasize=0",
          //   fileSizeAsHex(getSystemVanillaImage()), getSystemImage()),
      };
      // @formatter:on

      accCommand = mergeCommands(new String[] { expSettings.GETDELAYS, "-qdibec" },
          emuCommand);
    }

//    private String fileSizeAsHex(String path) {
//      return Long.toString((new File(path)).length(), 16);
//    }

    private String prefixFilePath(final String path) {
      final File f = new File(path);
      final String fileName = f.getName();
      String parentPath = f.getParent();
      if (!parentPath.isEmpty()) {
        parentPath = parentPath + "/";
      }
      return String.format("%semu-%d-%s", parentPath, emuId, fileName);
    }

    public String[] getAccountingCommand() {
      return accCommand;
    }

    public String getAdbName() {
      return adbName;
    }

    public int getAdbPort() {
      return adbPort;
    }

    public String getAvdName() {
      return avdName;
    }

    public int getControlPort() {
      return controlPort;
    }

    public String[] getEmuCommand() {
      return emuCommand;
    }

    public int getId() {
      return emuId;
    }

    public String getLogId() {
      return String.format("%d/%d", getId(), getControlPort());
    }

    public String getSdCardImage() {
      return sdCardImage;
    }

    public String getSdCardVanillaImage() {
      return expSettings.SDCARD_VIMAGE;
    }

    public ExperimentSettings getSettings() {
      return expSettings;
    }

    public String getSystemImage() {
      return systemImage;
    }

    public String getSystemVanillaImage() {
      return expSettings.SYSTEM_VIMAGE;
    }

    public String getUserdataImage() {
      return userdataImage;
    }

    public String getUserdataVanillaImage() {
      return expSettings.USERDATA_VIMAGE;
    }

  }

  /**
   * Exception class for signaling {@link Emulator} related errors.
   */
  public static class EmulatorException extends Exception {

    private static final long serialVersionUID = 5316637022917712153L;

    public EmulatorException() {
      super();
    }

    public EmulatorException(final String message) {
      super(message);
    }

    public EmulatorException(final String message, final Throwable cause) {
      super(message, cause);
    }

    public EmulatorException(final String message, final Throwable cause,
        final boolean enableSuppression, final boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
    }

    public EmulatorException(final Throwable cause) {
      super(cause);
    }
  }
}