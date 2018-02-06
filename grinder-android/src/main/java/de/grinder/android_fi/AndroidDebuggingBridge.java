package de.grinder.android_fi;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.grinder.android_fi.Emulator.EmulatorException;
import de.grinder.android_fi.ProcessUtils.ProcUtilException;

/**
 * ADB wrapper.
 *
 * TODO: full documentation
 *
 */
public class AndroidDebuggingBridge {

    static final int ADBSHELL_ADBERR = 42;

    static final int ADBSHELL_OUTERR = 43;

    private final String connectedDeviceName;

    private final Set<Process> createdProcesses;

    /** Instance level logger */
    private final Logger LOGGER;

    private final ExperimentSettings settings;

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

    private static Process startProcess(final boolean pipeIo, final String... command)
    throws AdbException {
        try {
            return ProcessUtils.startProcess(pipeIo, command);
        } catch (final ProcUtilException e) {
            final String msg = String.format("Failed to start ADB process: %s", e.getMessage());
            LoggerFactory.getLogger(AndroidDebuggingBridge.class).error(msg);
            throw new AdbException(msg, e);
        }
    }

    private static String[] varToArr(final String... args) {
        return args;
    }

    public AndroidDebuggingBridge(final ExperimentSettings settings,
                                  final String deviceName, final String logId) {
        LOGGER = LoggerFactory.getLogger(String.format("%s<%s>",
                                         AndroidDebuggingBridge.class.getName(), logId));
        this.settings = settings;
        this.connectedDeviceName = deviceName;
        createdProcesses = new HashSet<>(31);
    }

    private boolean addProcess(final Process p) {
        synchronized (createdProcesses) {
            return createdProcesses.add(p);
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

    private boolean removeProcess(final Process p) {
        synchronized (createdProcesses) {
            return createdProcesses.remove(p);
        }
    }

    private Process startAdbProcess(final boolean pipeIo, final String... cmd)
    throws AdbException {
        final Process p = startProcess(pipeIo,
                                       mergeCommands(varToArr(settings.ADB, "-s", connectedDeviceName), cmd));
        addProcess(p);
        return p;
    }

    private int waitForTermination(final Process p) throws AdbException {
        try {
            final int result = p.waitFor();
            return result;
        } catch (final InterruptedException e) {
            p.destroy();
            throw new AdbException("Interrupted while waiting for process", e);
        } finally {
            removeProcess(p);
        }
    }

    public void cancelCreatedProcesses() {
        synchronized (createdProcesses) {
            LOGGER.debug(lm("Cancelling %d created processes", createdProcesses.size()));
            for (final Process p : createdProcesses) {
                p.destroy();
            }
            createdProcesses.clear();
        }
    }

    /**
     * Checks whether the specified service is available.
     *
     * Uses the shell to query the service manager via 'service check'.
     *
     * @param serviceName
     *          The service name to check.
     * @return True if the service is running, false otherwise.
     *
     * @throws IOException
     * @throws AdbException
     */
    public boolean checkService(final String serviceName) throws AdbException {
        LOGGER.info(lm("Checking if service '%s' is running.", serviceName));

        final int result = execShell(String.format("service check %s | grep \"not found\"",
                                     serviceName));
        // service is running if grep returns != 0
        return result != 0;
    }

    public int execAdbCommand(final boolean waitTerm, final String... cmd)
    throws AdbException {
        LOGGER.debug(lm("Executing ADB command (%s): %s", waitTerm ? "blocking"
                        : "non-blocking", Arrays.toString(cmd)));

        final Process p = startAdbProcess(false, cmd);
        if (waitTerm) {
            return waitForTermination(p);
        }
        return 0;
    }

    public int execAdbCommand(final String... cmd) throws AdbException {
        return execAdbCommand(true, cmd);
    }

    public int execShell(final String cmd) throws AdbException {
        return execShell(cmd, true);
    }

    public int execShell(final String cmd, final boolean waitTerm) throws AdbException {
        LOGGER.debug(lm("Executing shell command (%s): %s", waitTerm ? "blocking"
                        : "non-blocking", cmd));

        final Process p = startProcess(false, settings.ADBSHELL, connectedDeviceName, cmd);
        addProcess(p);
        if (waitTerm) {
            final int ret = waitForTermination(p);
            if (ret == ADBSHELL_ADBERR) {
                final String msg = lm("Failed to execute ADB shell command [%s]. ADB error.", cmd);
                LOGGER.error(msg);
                throw new AdbException(msg);
            }
            return ret;
        }
        return 0;
    }

    public int execShell(final String cmd, final String pathExt) throws AdbException {
        return execShell(cmd, pathExt, true);
    }

    public int execShell(final String cmd, final String pathExt, final boolean waitTerm)
    throws AdbException {
        final String path = String.format("PATH=%s:$PATH", pathExt);
        return execShell(String.format("%s; %s", path, cmd), waitTerm);
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public String getState() throws AdbException {
        LOGGER.info(lm("Getting device state"));
        // TODO make this nicer, esp. error handling

        final Process p = startAdbProcess(true, "get-state");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        p.getInputStream()))) {
            waitForTermination(p);
            final String state = reader.readLine();
            LOGGER.debug(lm("Device state is: %s", state));
            return state;
        } catch (final IOException e) {
            throw new AdbException(e);
        }
    }

    public void loadModule(final String modulePath) throws AdbException {
        LOGGER.info(lm("Loading kernel module [%s]", modulePath));

        final int ret = execShell(String.format("insmod %s", modulePath));
        if (ret != 0) {
            final String msg = lm("Failed to load kernel module [%s]: error %d", modulePath,
                                  ret);
            LOGGER.error(msg);
            throw new AdbException(msg);
        } else {
            LOGGER.debug(lm("Successfully loaded module [%s]", modulePath));
        }
    }

    public File pullFile(final String path) throws IOException, AdbException {
        LOGGER.info(lm("Pulling file [%s]", path));
        final String localPath = String.format("%s/%s-%s",
                                               System.getProperty("java.io.tmpdir", "/tmp"), (new File(path)).getName(),
                                               UUID.randomUUID());
        final int ret = execAdbCommand("pull", path, localPath);
        if (ret != 0) {
            final String msg = lm("Failed to pull [%s] to [%s]: %d", path, localPath, ret);
            LOGGER.error(msg);
            throw new AdbException(msg);
        } else {
            LOGGER.debug(lm("Successfully pulled [%s] to [%s]", path, localPath));
        }
        return new File(localPath);
    }

    /**
     * Loads a kernel object to the running android.
     *
     * Uses adb to push the compiled kernel module to the emulated android. Loads the pushed
     * file as a kernel object.
     *
     * @param module
     *          The file containing the kernel object
     * @throws IOException
     * @throws AdbException
     */
    public void pushLoadModule(final File module) throws IOException, AdbException {
        LOGGER.info(lm("Pushing and loading kernel module [%s]", module.getName()));

        final int ret = execAdbCommand("push", module.getAbsolutePath(),
                                       settings.AVD_MODULE_DIR);
        if (ret != 0) {
            final String msg = lm("Failed to push module [%s]", module.getName());
            LOGGER.error(msg);
            throw new AdbException(msg);
        } else {
            LOGGER.debug(lm("Successfully pushed module [%s]", module.getName()));
        }
        loadModule(settings.AVD_MODULE_DIR + "/" + module.getName());
    }

    /**
     * Removes the specified file from the device. This is a convenience function for
     * {@link #removeFile(String, boolean)}.
     *
     * The attempt to remove a non-existing file is handled as an error.
     *
     * @param path
     *          File to remove on device. File must exist.
     * @throws AdbException
     *           If something with ADB goes wrong or the file could not be removed.
     */
    public void removeFile(final String path) throws AdbException {
        removeFile(path, false);
    }

    /**
     * Removes the specified file from the device.
     *
     * @param path
     *          File to remove on device. Depending on the {@code ignoreNotExist} parameter,
     *          the file must not necessarily exist.
     * @param ignoreNotExist
     *          If {@code true}, the attempt to remove a non-existing file won't be handled
     *          as an error
     * @throws AdbException
     *           If something with ADB goes wrong or the file could not be removed.
     */
    public void removeFile(final String path, final boolean ignoreNotExist)
    throws AdbException {
        LOGGER
        .info(lm("Removing file [%s](%s)", path, ignoreNotExist ? "force" : "no-force"));
        final int ret = execShell(String.format("rm %s%s", ignoreNotExist ? "-f " : "", path));
        if (ret != 0) {
            final String msg = lm("Failed to remove file [%s]: %d", path, ret);
            LOGGER.error(msg);
            throw new AdbException(msg);
        } else {
            LOGGER.debug(lm("Successfully removed file [%s]", path));
        }
    }

    /**
     * Starts the given Android application.
     *
     * Uses the adb to start the given Android application.
     *
     * Blocks only until the application has launched, but does not wait for the application
     * to exit. Note that the error checking here is really bad -- due to the android
     * ActivityMAnager command line facility.
     *
     * @param application
     *          The Android application in the form [package]/[class]
     * @throws IOException
     * @throws AdbException
     */
    public void startApp(final String application) throws AdbException {
        LOGGER.info(lm("Starting Android app [%s]", application));
        final int ret = execShell(String.format("am start %s", application));
//    int ret = execShell(String.format("am start -W %s", application));
        if (ret != 0) {
            final String msg = lm("Failed to start app [%s]", application);
            LOGGER.error(msg);
            throw new AdbException(msg);
        } else {
            LOGGER.debug(lm("Assume successful start of app [%s]", application));
        }
    }

    public boolean testResponse(final long timeOut) throws AdbException {
        int runTime = 0;
        final int sleepTime = 1000;
        final Process test = startAdbProcess(false, "shell", "ls");

        while (runTime < timeOut) {

            try {
                test.exitValue();
                return true;
            } catch (final IllegalThreadStateException e) {
                // if test is still running -> probably hang
            }

            try {
                Thread.sleep(sleepTime);
                runTime += sleepTime;
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }

        test.destroy();
        removeProcess(test);
        return false;
    }

    /**
     * Waits until the activity manager is available.
     *
     * @throws AdbException
     */
    public void waitForActivityManager() throws AdbException {
        LOGGER.info(lm("Waiting for Activity Manager to become available"));

        final Process p = startAdbProcess(true, "logcat", "-b", "events",
                                          "boot_progress_ams_ready:I", "*:S");
        final Scanner pout = new Scanner(p.getInputStream());

        if (!pout.hasNext()) {
            // logcat was terminated
            // just to be on the safe side
            p.destroy();
            removeProcess(p);
            pout.close();
            throw new AdbException("logcat terminated unexceptedly");
        }

        LOGGER.debug(lm("Activity Manager is now available"));
        p.destroy();
        removeProcess(p);
        pout.close();
    }

    /**
     * Waits until the device is ready.
     *
     * Note that this method waits forever if the device, i.e., the emulator, never comes
     * online. Especially, if the emulator process is not running, e.g., due to a crash. Use
     * {@link #waitForDeviceChecked(Emulator)} to wait for an emulator only if it is
     * running.
     *
     * @throws AdbException
     *           If something goes wrong during the waiting.
     */
    public void waitForDevice() throws AdbException {
        LOGGER.info(lm("Waiting for device"));
        final int ret = execAdbCommand("wait-for-device");
        if (ret != 0) {
            final String msg = lm("Failed to wait for device: %d", ret);
            LOGGER.error(msg);
            throw new AdbException(msg);
        } else {
            LOGGER.debug(lm("Waiting for device finished"));
        }
    }

    public boolean waitForDeviceChecked(final Emulator emu) throws AdbException {
        class WaitThread extends Thread {
            protected Thread other;

            protected void killOther() {
                other.interrupt();
            }

            public void setOther(final Thread other) {
                this.other = other;
            }
        }
        class WaitEmuThread extends WaitThread {
            @Override
            public void run() {
                try {
                    emu.awaitEmuTerminationSilent();
                    LOGGER.debug("WaitEmuThread: emulator terminated");
                } catch (final EmulatorException e) {
                    LOGGER.debug(String.format("WaitEmuThread: %s", e.getMessage()));
                } finally {
                    killOther();
                }
            }
        }
        class WaitDevThread extends WaitThread {
            private boolean success = false;

            @Override
            public void run() {
                try {
                    waitForDevice();
                    success = true;
                } catch (final AdbException e) {
                    LOGGER.debug(String.format("WaitDevThread: %s", e.getMessage()));
                } finally {
                    killOther();
                }
            }

            public boolean success() {
                return success;
            }
        }

        final WaitDevThread devWaitThread = new WaitDevThread();
        final WaitEmuThread emuWaitThread = new WaitEmuThread();
        devWaitThread.setOther(emuWaitThread);
        emuWaitThread.setOther(devWaitThread);
        devWaitThread.start();
        emuWaitThread.start();

        try {
            devWaitThread.join();
            emuWaitThread.join();
            return devWaitThread.success();
        } catch (final InterruptedException e) {
            final String msg = String.format("Failed to wait for device. Interrupted. %s",
                                             e.getMessage());
            LOGGER.error(msg, e);
            devWaitThread.interrupt();
            emuWaitThread.interrupt();
            throw new AdbException(msg, e);
        }
    }

    /**
     * Wait until the specified message is observed in the logcat output.
     *
     * The considered logcat output is limited to the specified buffer and filtered
     * according to the given filter expressions. The remaining logcat output is searched
     * for the specified search string, which is interpreted as Java regular expression.
     * This method blocks until the specified search string is observed for the first time.
     * This method waits forever if the search string is never observed.
     * <p>
     * Logcat filters have the following format: {@code <componentName>:<logLevel>}, where
     * {@code <componentName>} is either {@code '*'} or the name of a given component, and
     * {@code <logLevel>} is one of the following letters:<br>
     * <ul>
     * <li>{@code v} -- verbose log level</li>
     * <li>{@code d} -- debug log level</li>
     * <li>{@code i} -- informative log level</li>
     * <li>{@code w} -- warning log level</li>
     * <li>{@code e} -- error log level</li>
     * <li>{@code s} -- silent log level</li>
     * </ul>
     * For example, the following only displays messages from the 'GSM' component that are
     * at least at the informative level: {@code *:s GSM:i}. Note that the silent level
     * should not be specified here since it is on by default to silence unwanted messages.
     * <p>
     * Note that this method is <b>interruptible</b> and reacts to logcat stream close.
     *
     * @param buffer
     *          The logcat buffer to observe. Possible values are {@code "main"},
     *          {@code "system"} or {@code "events"}.
     * @param searchStr
     *          The string for which to search in the logcat output message. This is
     *          interpreted as Java regular expression.
     * @param filters
     *          One or more logcat filter specifications.
     * @throws AdbException
     *           If something went wrong, e.g., the emulator got killed or the thread was
     *           interrupted.
     */
    public void waitForLogcatEvent(final String buffer, final String searchStr,
                                   final String... filters) throws AdbException {
        LOGGER.info(lm("Waiting for logcat event: %s", searchStr));
        final Process p = startAdbProcess(true,
                                          mergeCommands(varToArr("logcat", "-s", "-b", buffer), filters));
        final InputStream procIn = p.getInputStream();

        try (final InputStreamReader isr = new InputStreamReader(procIn);
                    final BufferedReader reader = new BufferedReader(isr)) {
            String line;
            final Pattern regex = Pattern.compile(searchStr);

            while (true) {
                // wait for complete line
                while (!reader.ready()) {
                    // ensure that we get an exception if process stream has been closed
                    procIn.available();
                    Thread.sleep(200);
                }
                line = reader.readLine();
                if (line != null) {
                    final Matcher mtch = regex.matcher(line);
                    if (mtch.find()) {
                        return; // saw expected message
                    }
                } else {
                    break;
                }
            }
        } catch (final IOException | InterruptedException e) {
            // reader error, should be due to logcat termination: logcat error or timeout
            final String msg = lm("Failed to wait for logcat event [%s] with filters %s: %s",
                                  searchStr, Arrays.toString(filters), e.getMessage());
            LOGGER.error(msg, e);
            throw new AdbException(msg, e);
        } finally {
            p.destroy();
            removeProcess(p);
        }

        // we should never reach this point since this means we reached the end of the stream
        // without seeing the expected message
        final String msg = lm(
                               "Failed to wait for logcat event [%s] with filters %s. End of stream.",
                               searchStr, Arrays.toString(filters));
        LOGGER.error(msg);
        throw new AdbException(msg);
    }

    /**
     * More robust version of {@link #waitForLogcatEvent(String, String, String...)} that
     * explicitly checks whether the specified emulator instance is alive.
     *
     * Usually, the logcat command should terminate as soon as the associated emulator dies.
     * However, with bad timing, it can happen that the logcat command is issued right after
     * the emulator died, which leads to a wait-forever situation. This method recognizes
     * this particular situation as well as general emulator death situations independent of
     * the actual logcat command.
     *
     * @param emu
     *          Emulator instance to check for.
     * @param buffer
     *          The logcat buffer to observe. Possible values are {@code "main"},
     *          {@code "system"} or {@code "events"}.
     * @param searchStr
     *          The string for which to search in the logcat output message. This is
     *          interpreted as Java regular expression.
     * @param filters
     *          One or more logcat filter specifications.
     * @throws AdbException
     *           If something went wrong, e.g., the emulator got killed or the thread was
     *           interrupted.
     */
    public void waitForLogcatEventChecked(final Emulator emu, final String buffer,
                                          final String searchStr, final String... filters) throws AdbException {
        class WaitThread extends Thread {
            protected Thread other;

            protected void killOther() {
                other.interrupt();
            }

            public void setOther(final Thread other) {
                this.other = other;
            }
        }
        class WaitEmuThread extends WaitThread {
            @Override
            public void run() {
                try {
                    emu.awaitEmuTerminationSilent();
                    LOGGER.debug("WaitEmuThread: emulator terminated");
                } catch (final EmulatorException e) {
                    LOGGER.debug(String.format("WaitEmuThread: %s", e.getMessage()));
                } finally {
                    killOther();
                }
            }
        }
        class WaitLogcatThread extends WaitThread {
            private AdbException except = null;

            @Override
            public void run() {
                try {
                    waitForLogcatEvent(buffer, searchStr, filters);
                } catch (final AdbException e) {
                    LOGGER.debug(String.format("WaitLogcatThread: %s", e.getMessage()));
                    except = e;
                } finally {
                    killOther();
                }
            }

            public AdbException except() {
                return except;
            }
        }

        final WaitLogcatThread lcWaitThread = new WaitLogcatThread();
        final WaitEmuThread emuWaitThread = new WaitEmuThread();
        lcWaitThread.setOther(emuWaitThread);
        emuWaitThread.setOther(lcWaitThread);
        lcWaitThread.start();
        emuWaitThread.start();

        try {
            lcWaitThread.join();
            emuWaitThread.join();
            if (lcWaitThread.except() != null) {
                throw lcWaitThread.except();
            }
        } catch (final InterruptedException e) {
            final String msg = String.format("Failed to wait for device. Interrupted. %s",
                                             e.getMessage());
            LOGGER.error(msg, e);
            lcWaitThread.interrupt();
            emuWaitThread.interrupt();
            throw new AdbException(msg, e);
        }
    }

    /**
     * Waits until the specified service is running.
     *
     * @param serviceName
     *          Service to wait for.
     * @param pollIntervall
     *          Milliseconds to sleep between service checks
     *
     * @throws IOException
     * @throws AdbException
     * @throws InterruptedException
     */
    public void waitForService(final String serviceName, final long pollIntervall)
    throws AdbException {
        LOGGER.info(lm("Waiting for service '%s' to become available, polling every %d ms.",
                       serviceName, pollIntervall));

        while (!checkService(serviceName)) {
            LOGGER.debug(lm("Service '%s' not yet available, going to sleep.", serviceName));
            try {
                Thread.sleep(pollIntervall);
            } catch (final InterruptedException e) {
                final String msg = lm("Failed to wait for service '%s'. Interrupted", serviceName);
                LOGGER.error(msg);
                throw new AdbException(msg);
            }
        }
        LOGGER.debug(lm("Service '%s' is available now.", serviceName));
    }

    /**
     * Exception class for signaling {@link AndroidDebuggingBridge} related errors.
     */
    public static class AdbException extends Exception {

        private static final long serialVersionUID = 1351439431289062480L;

        public AdbException() {
            super();
        }

        public AdbException(final String message) {
            super(message);
        }

        public AdbException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public AdbException(final String message, final Throwable cause,
                            final boolean enableSuppression, final boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }

        public AdbException(final Throwable cause) {
            super(cause);
        }
    }
}
