package de.grinder.android_fi;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.grinder.android_fi.AndroidDebuggingBridge.AdbException;
import de.grinder.android_fi.Emulator.EmulatorException;
import de.grinder.android_fi.ExperimentSettings.SettingsException;
import de.grinder.android_fi.experimentResultDetection.CandeInterface;
import de.grinder.android_fi.experimentResultDetection.ExternalDetector;
import de.grinder.util.cue.CUEAbstraction;

/**
 * This class provides an {@link CUEAbstraction} for an emulated Android.
 * 
 * This class uses the android debugging bridge (ADB) to control the running emulated
 * Android. Multiple instances of this class can be used to control multiple emulators in
 * parallel. However, this requires careful configuration and preparation of the GRINDER
 * database and experiment starts in the right order. See also {@link Emulator},
 * {@link EmulatorFactory} and {@link AndroidDebuggingBridge}.
 * 
 */
public class EmulatedAndroid implements CUEAbstraction {

	private final String ioschedulerSpecFile = "/sys/block/mmcblk0/queue/scheduler";
	//private final String ioschedTestcase = "^(?<schedName>\\w+)-iosched(?:\\.i_\\w+_\\d+)\\.ko$";
	private final String ioschedTestcase = "^(?<schedName>\\w+)-iosched(?:\\.i_\\w+_\\d+)+\\.ko$";
	
  private final AndroidDebuggingBridge adb;

  private final CandeInterface cande;

  private final Emulator emulator;

  /** Proxy for communication with GRINDER. */
  private final AndroidGrinderProxy grinder;

  /** Instance level logger */
  private final Logger LOGGER;

  private final ExternalDetector resultDetector;

  /**
   * Lock for preventing that multiple threads execute the experiment run code
   * concurrently.
   */
  private final Object runLock = new Object();

  /** Settings properties for system dependent tools and paths. */
  private final ExperimentSettings settings;

  private static String joinStr(final String sep, final List<String> lst) {
    final StringBuilder sb = new StringBuilder();
    for (final String s : lst) {
      if (sb.length() > 0) {
        sb.append(sep);
      }
      sb.append(s);
    }
    return sb.toString();
  }

  /**
   * Constructs an adapter for an Android system running in an emulator
   */
  public EmulatedAndroid() {

    try {
      settings = new ExperimentSettings();
    } catch (final SettingsException e) {
      LoggerFactory.getLogger(EmulatedAndroid.class).error(
          "Failed to load experiment settings: {}", e.getMessage());
      throw new RuntimeException("Experiment settings load failure", e);
    }
    emulator = EmulatorFactory.getInstance().getNewEmu(settings);
    adb = emulator.getAdb();
    LOGGER = LoggerFactory.getLogger(String.format("%s<%s>",
        EmulatedAndroid.class.getName(), emulator.getConfig().getLogId()));

    // !OS! - hack for multiple experiment (= target) instances: port + emuId -- since we
    // need one grinder server per target; note that it is crucial to create the targets
    // with consecutive ports starting at <settings.GRINDER_PORT>; moreover, the
    // experiment runs for the targets must be started in the order of their ports to
    // ensure that the emulator instances are created in the correct order; failing to do
    // this will mess up the server connections and the EmulatedAndroid instance will
    // communicate with the wrong GRINDER server!!
    grinder = new AndroidGrinderProxy(settings.GRINDER_HOST, settings.GRINDER_PORT
        + emulator.getConfig().getId());
    resultDetector = new ExternalDetector(emulator, settings, grinder,
        emulator.getEmulatorOutputCapturer(), emulator.getConfig().getLogId());
    cande = new CandeInterface(adb, settings);
  }

  private void delayedsignalExperimentFail() {
    resultDetector.signalDelayedExperimentFailure(5);
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
   * Stop running instances of the emulator.
   */
  @Override
  public void reset() {
    LOGGER.info(lm("Resetting emulated Android"));
    resultDetector.stopAllDetectors();
    adb.cancelCreatedProcesses();
    try {
      emulator.kill();
    } catch (final EmulatorException e) {
      LOGGER.error(lm("Failed to stop experiment: %s", e.getMessage()), e);
      throw new RuntimeException("Failed to stop experiment", e);
    }

    try {
      grinder.sendLog(joinStr("\n", emulator.getAccountingStats()));
      Thread.sleep(800); // Wait longer than the servers reset finish poll
                         // interval to get a higher probability that the log
                         // message arrives safe and sound at the server before the
                         // experiment result is written to the DB
    } catch (final IOException e) {
      final String msg = lm("Failed to log accounting stats: %s", e.getMessage());
      LOGGER.error(msg, e);
    } catch (final InterruptedException e) {
      // just ignore, waiting is a hack anyway
    }
  }

  /**
   * Execute an experiment on the emulated android device.
   * 
   * The experiment consists of the following steps:
   * <ol>
   * <li>Start emulator and Android system
   * <li>Load modules required for experiment
   * <li>Execute the workload
   * </ol>
   */
  @Override
  public void runExperiment() {
    synchronized (runLock) {
      LOGGER.info(lm("Starting experiment run"));

      try {
        resultDetector.rearm();
        resultDetector.startSysinitDetector();

        try {
          emulator.start();
        } catch (final EmulatorException e) {
          throw new ExperimentFailure(lm("Failed to start emulator: %s", e.getMessage()),
              e);
        }

        LOGGER.info(lm("Waiting for emulator boot-up"));
        if (!adb.waitForDeviceChecked(emulator)) {
          LOGGER.error("Emulator died while waiting for its boot-up.");
          throw new ExperimentFailure("Emulator died prematurely.");
        }
        LOGGER.info(lm("Removing external detector flag file"));
        adb.removeFile(settings.AVD_WORKLOAD_FLAGFILE, true);

        // start detector at this point since only loading the modules can
        // lead to a kernel panic (etc.) caused by the module_init method
        LOGGER.info(lm("Starting error detection engine"));
        cande.startCande();
        resultDetector.startSystemDetectors();

        // Load modules
        // note that grinder is already part of the system image
        LOGGER.info(lm("Loading kernel modules"));
        try {
          adb.loadModule(String.format("%s/%s", settings.AVD_MODULE_DIR, grinder
              .getInstrumentedKernelModule().getName()));
        } catch (final IOException e) {
          throw new ExperimentFailure(lm("Communication failure with GRINDER server: %s",
              e.getMessage()), e);
        }
        
        // check if the loaded mutant is an io scheduler
        // if so, configure the kernel to use it 
        Pattern ioschedPattern = Pattern.compile(ioschedTestcase);
        Matcher ioschedMatcher = ioschedPattern.matcher(grinder.getInstrumentedKernelModule().getName());
        if (ioschedMatcher.matches()){
        	adb.execShell(String.format("echo %s > %s", ioschedMatcher.group("schedName"),ioschedulerSpecFile));
        	LOGGER.debug(lm("IOSched experiment detected: configured %s to use %s",
        			ioschedulerSpecFile,ioschedMatcher.group("schedName")));
        }
        
        // Workload autostarts, but we have to wait for it
        LOGGER.info(lm("Waiting for workload start"));
        adb.waitForLogcatEventChecked(emulator, "main", "workload_started", "Workload:D");
        resultDetector.stopSysinitDetector();
        LOGGER.info(lm("Signaling Detector"));
        resultDetector.startApplicationDetectors();
        // NOTE
        // the result detector may signal the finish before the actual app
        // finishes. In this case 'stop' or 'reset' may be called

        LOGGER.info(lm("Experiment logic finished. Waiting for results."));

      } catch (final AdbException e) {
        LOGGER.error(lm("ADB failure in experiment logic: %s", e.getMessage()), e);
        delayedsignalExperimentFail();
      } catch (final ExperimentFailure e) {
        LOGGER.error(lm("Experiment logic signaled failure: %s", e.getMessage()), e);
        delayedsignalExperimentFail();
      } catch (final Throwable t) {
        LOGGER.error(lm("Unexpected error in experiment logic: %s", t.getMessage()), t);
        delayedsignalExperimentFail();
      }
    }
  }

  /**
   * Starts the emulated Android
   */
  @Override
  public void start() {
    LOGGER.info(lm("Starting emulated Android"));
    try {
      grinder.connect();
    } catch (final IOException e) {
      LOGGER.error(lm("Failed to start %s: %s", this, e.getMessage()), e);
      throw new RuntimeException("Connection to GRINDER failed", e);
    }
  }

  /**
   * Stops the emulated Android.
   */
  @Override
  public void stop() {
    LOGGER.info(lm("Stopping emulated Android"));
    try {
      grinder.disconnect();
    } catch (final IOException e) {
      // Do nothing, the connection has already been closed.
    }
    resultDetector.stopAllDetectors();
    adb.cancelCreatedProcesses();
    try {
      emulator.kill();
    } catch (final EmulatorException e) {
      LOGGER.error(lm("Failed to stop experiment: %s", e.getMessage(), e));
      throw new RuntimeException("Failed to stop experiment", e);
    }
  }

  /**
   * Exception class for signaling {@link EmulatedAndroid} related errors, which are
   * usually errors detected within the experiment logic.
   */
  public static class ExperimentFailure extends Exception {

    private static final long serialVersionUID = 8533477928280119374L;

    public ExperimentFailure() {
      super();
    }

    public ExperimentFailure(final String message) {
      super(message);
    }

    public ExperimentFailure(final String message, final Throwable cause) {
      super(message, cause);
    }

    public ExperimentFailure(final String message, final Throwable cause,
        final boolean enableSuppression, final boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
    }

    public ExperimentFailure(final Throwable cause) {
      super(cause);
    }

  }
}
