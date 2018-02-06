package de.grinder.android_fi.experimentResultDetection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.grinder.android_fi.AndroidDebuggingBridge;
import de.grinder.android_fi.AndroidDebuggingBridge.AdbException;
import de.grinder.android_fi.Emulator;
import de.grinder.android_fi.ExperimentSettings;
import de.grinder.util.message.Proxy;

public class ExternalDetector {

    private final AndroidDebuggingBridge adb;
    private final InputStreamCapturer consoleOutput;
    private final List<ScheduledFuture<?>> detectors;
    private final Emulator emulator;
    // private ScheduledFuture<?> applicationInstallationFailureDetector;
    /** Flag for preventing detector starts after a stop call was issued. */
    private volatile boolean isArmed = false;
    private final Logger LOGGER;
    private MutationStimulatedDetector mutationDetector = null;
    private final Proxy proxy;
    private final ScheduledExecutorService scheduler;
    private final ExperimentSettings settings;
    private ScheduledFuture<?> sysinitDetector = null;

    /**
     * Checks target device for experiment results and signals them to the targetController
     * if available
     *
     * @param adb
     * @param proxy
     * @throws IOException
     */
    public ExternalDetector(final Emulator emu, final ExperimentSettings settings,
                            final Proxy proxy, final InputStreamCapturer consoleCapturer, final String logId) {
        LOGGER = LoggerFactory.getLogger(String.format("%s<%s>",
                                         ExternalDetector.class.getName(), logId));
        this.emulator = emu;
        this.adb = emu.getAdb();
        this.settings = settings;
        this.proxy = proxy;
        scheduler = Executors.newScheduledThreadPool(6);
        consoleOutput = consoleCapturer;
        detectors = new ArrayList<ScheduledFuture<?>>(8);
    }

    private boolean isAccountingRunning() {
        return emulator.isAccRunning();
    }

    private boolean isEmulatorRunningAdb() throws AdbException {
        return !adb.getState().equals("unknown");
    }

    private boolean isEmulatorRunningEmu() {
        return emulator.isEmuRunning();
    }

    private void signalResult(final ExperimentResult result) {
        stopAllDetectors();
        LOGGER.info("Detected experiment end with result: {}", result.toString());
        try {
            String activated = "";
            if (mutationDetector != null) {
                mutationDetector.run();
                activated = mutationDetector.mutationLogString();
            }
            proxy.sendExperimentFinished(result.getId(), activated);
        } catch (final IOException e) {
            LOGGER
            .error(String.format("Failed to send experiment finish signal: %s",
                                 e.getMessage()), e);
        }
    }

    private void stopDetector(final ScheduledFuture<?> detector) {
        if (!detector.isDone() && !detector.cancel(true)) {
            LOGGER.error("Failed to stop detector: {}", detector.toString());
        }
    }

    private boolean testWorkloadFlag(final String value) throws AdbException {
        boolean result = false;
        try {
            final File flagFile = adb.pullFile(settings.AVD_WORKLOAD_FLAGFILE);
            if (flagFile != null && flagFile.exists()) {
                final BufferedReader reader = new BufferedReader(new FileReader(flagFile));
                final String content = reader.readLine();
                if (content != null) {
                    result = content.equals(value);
                }
                try {
                    reader.close();
                } catch (final Exception e) {
                    LOGGER.warn(
                        String.format("Failed to properly close flag file reader: %s",
                                      e.getMessage()), e);
                }

                try {
                    Files.delete(flagFile.toPath());
                } catch (final Exception e) {
                    LOGGER.warn(
                        String.format("Workload flag file was not deleted: %s", e.getMessage()), e);
                }
            }
        } catch (final IOException e) {
            LOGGER.error(String.format("Failed to test flag file: %s", e.getMessage()), e);
        }
        return result;
    }

    public boolean isArmed() {
        return isArmed;
    }

    public void rearm() {
        isArmed = true;
    }

    public void signalDelayedExperimentFailure(final int timesPeriod) {
        synchronized (detectors) {
            if (isArmed) {
                LOGGER.debug("Delaying EXPERIMENT_FAILURE signal");
                detectors
                .add(scheduler.scheduleWithFixedDelay(new DelayedExperimentFailureNotifier(),
                                                      settings.DETECTION_PERIOD * timesPeriod, settings.DETECTION_PERIOD
                                                      * timesPeriod, TimeUnit.SECONDS));
            } else {
                LOGGER.debug("Skipped delaying of EXPERIMENT_FAILURE signal. Not armed.");
            }
        }
    }

    public void startApplicationDetectors() {
        LOGGER.info("Starting application detectors");
        synchronized (detectors) {
            if (isArmed) {
                detectors.add(scheduler.scheduleWithFixedDelay(new ApplicationFinishedDetector(),
                              settings.DETECTION_PERIOD, settings.DETECTION_PERIOD, TimeUnit.SECONDS));
                detectors.add(scheduler.scheduleWithFixedDelay(new ApplicationFailureDetector(),
                              settings.DETECTION_PERIOD, settings.DETECTION_PERIOD, TimeUnit.SECONDS));
                detectors.add(scheduler.scheduleWithFixedDelay(new ApplicationHangAssumer(),
                              settings.APP_HANG_ASSUMPTION_DELAY, settings.APP_HANG_ASSUMPTION_DELAY,
                              TimeUnit.SECONDS));
            } else {
                LOGGER.warn("Skipped application detectors start. Not armed.");
            }
        }
    }

    public void startSysinitDetector() {
        LOGGER.info("Starting system init detector");
        synchronized (detectors) {
            if (isArmed) {
                sysinitDetector = scheduler.scheduleWithFixedDelay(new SysinitHangAssumer(),
                                  settings.SYSINIT_HANG_ASSUMPTION_DELAY,
                                  settings.SYSINIT_HANG_ASSUMPTION_DELAY, TimeUnit.SECONDS);
                detectors.add(sysinitDetector);
            } else {
                LOGGER.warn("Skipped sysinit detector start. Not armed.");
            }
        }
    }

    public void startSystemDetectors() {
        LOGGER.info("Starting system detectors");
        synchronized (detectors) {
            if (isArmed) {
                mutationDetector = new MutationStimulatedDetector();
                detectors.add(scheduler.scheduleWithFixedDelay(mutationDetector, 0,
                              settings.DETECTION_PERIOD, TimeUnit.SECONDS));
                detectors.add(scheduler.scheduleWithFixedDelay(new SystemHangAndCrashDetector(),
                              settings.DETECTION_PERIOD, settings.DETECTION_PERIOD, TimeUnit.SECONDS));
                detectors.add(scheduler.scheduleWithFixedDelay(new SystemHangAssumer(),
                              settings.SYSTEM_HANG_ASSUMPTION_DELAY,
                              settings.SYSTEM_HANG_ASSUMPTION_PERIOD, TimeUnit.SECONDS));
            } else {
                LOGGER.warn("Skipped system detectors start. Not armed.");
            }
        }
    }

    public void stopAllDetectors() {
        LOGGER.info("Stopping all detector");
        isArmed = false;
        synchronized (detectors) {
            for (final ScheduledFuture<?> detector : detectors) {
                stopDetector(detector);
            }
            sysinitDetector = null;
            detectors.clear();
        }
    }

    public void stopSysinitDetector() {
        LOGGER.info("Stopping system init detector");
        synchronized (detectors) {
            if (sysinitDetector != null) {
                stopDetector(sysinitDetector);
                detectors.remove(sysinitDetector);
                sysinitDetector = null;
            }
        }
    }

    private class ApplicationFailureDetector implements Runnable {
        @Override
        public void run() {
            try {
                if (consoleOutput
                        .contains("Error: Activity class {de.grinder.android_fi/de.grinder.android_fi.Workload} does not exist.")
                        || testWorkloadFlag("workload_failed")) {
                    signalResult(ExperimentResult.APPLICATION_FAULT_DETECTED);
                }
            } catch (final AdbException e) {
                // do nothing, this branch is taken if the adb hasn't responded
            }
        }
    }

    private class ApplicationFinishedDetector implements Runnable {
        @Override
        public void run() {
            try {
                if (testWorkloadFlag("workload_finished")) {
                    signalResult(ExperimentResult.FINISHED);
                }
            } catch (final AdbException e) {
                // do nothing, this branch is taken if the adb hasn't responded
            }
        }
    }

    private class ApplicationHangAssumer implements Runnable {
        @Override
        public void run() {
            // if adb responses after the specified delay a application hang is
            // assumed
            try {
                if (isEmulatorRunningEmu() && adb.testResponse(settings.ADB_TIMEOUT_DELAY)) {
                    signalResult(ExperimentResult.APPLICATION_HANG_ASSUMED);
                }
            } catch (final AdbException e) {
                // do nothing, this branch is taken if the adb hasn't responded
            }
        }
    }

    private class DelayedExperimentFailureNotifier implements Runnable {
        @Override
        public void run() {
            LOGGER.debug("Executing delayed EXPERIMENT_FAILURE signal");
            signalResult(ExperimentResult.EXPERIMENT_FAILURE);
        }
    }

    private class MutationStimulatedDetector implements Runnable {
        private final Set<String> seenMutations = new HashSet<>();
        final Pattern mutantPrintk = Pattern.compile("^(.+)-fault injection (\\(.*\\))$");

        public List<String> mutationList() {
            List<String> lst;
            synchronized (seenMutations) {
                lst = new ArrayList<>(seenMutations);
            }
            Collections.sort(lst);
            return lst;
        }

        public String mutationLogString() {
            final List<String> mutList = mutationList();
            if (mutList.size() == 0) {
                return "";
            } else {
                final StringBuilder sb = new StringBuilder();
                for (final String mut : mutList) {
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    sb.append(mut);
                }
                return sb.toString();
            }
        }

        @Override
        public void run() {
            final Scanner output = new Scanner(consoleOutput.getOutput());
            while (output.hasNextLine()) {
                final Matcher match = mutantPrintk.matcher(output.nextLine());
                if (!match.matches()) {
                    continue;
                }

                final String module = match.group(1);
                /* TODO: sanity check for mutant printk line
                if (!module.equals(grinder.getInstrumentedKernelModule().getName())) {
                  continue;
                } */
                final String fault = match.group(2);
                synchronized (seenMutations) {
                    if (seenMutations.contains(fault)) {
                        LOGGER.debug(String.format("Saw mutation '%s' again.", fault));
                        continue;
                    }
                    seenMutations.add(fault);
                }
                LOGGER.info(String.format("Mutation '%s' was stimulated", fault));
            }
            output.close();
        }
    }

    private class SysinitHangAssumer implements Runnable {
        @Override
        public void run() {
            // do we want to add more elaborate stuff, e.g., a periodic detector that checks
            // the emulator process existence before ADB is available right at the beginning?

            LOGGER.info("System init hang assumer fires -- "
                        + "assuming hang during system initialization");
            if (!isAccountingRunning()) {
                LOGGER.error("Accounting is not running");
            }
            if (!isEmulatorRunningEmu()) {
                LOGGER.error("Emulator process is not running");
            }
            try {
                if (!isEmulatorRunningAdb()) {
                    LOGGER.error("ADB says emulator is not running");
                }
            } catch (final AdbException e) {
                LOGGER.error("Failed to query emulator state via ADB: {}", e.getMessage(), e);
            }

            signalResult(ExperimentResult.SYSINIT_HANG_ASSUMED);
        }
    }

    private class SystemHangAndCrashDetector implements Runnable {
        @Override
        public void run() {
            if (consoleOutput.contains("Kernel panic")) {
                // this may be a simple kernel panic or a detected system hang
                final String consoleOut = consoleOutput.getOutput();
                final int startIdx = consoleOut.indexOf("Kernel panic");
                final int endIdx = consoleOut.indexOf("\n", startIdx);
                final String line = consoleOut.substring(startIdx, endIdx);
                if (line.contains("Hang detected")) {
                    // first kernel panic caused by hang detector
                    signalResult(ExperimentResult.SYSTEM_HANG_DETECTED);
                } else {
                    // not hang detector related
                    LOGGER.debug("SYSTEM CRASH detected: kernel panic message");
                    signalResult(ExperimentResult.SYSTEM_CRASH_DETECTED);
                }
            } else if (!isEmulatorRunningEmu() || consoleOutput.contains("qemu: fatal:")) {
                LOGGER.debug("SYSTEM CRASH detected: emu not running or QEMU error message");
                signalResult(ExperimentResult.SYSTEM_CRASH_DETECTED);
            } else if (consoleOutput.contains("Internal error: Oops:")) {
                signalResult(ExperimentResult.SYSTEM_OOPS_DETECTED);
            }
        }
    }

    private class SystemHangAssumer implements Runnable {
        @Override
        public void run() {
            // if adb doesn't respond assume system hang
            try {
                if (isEmulatorRunningEmu() && !adb.testResponse(settings.ADB_TIMEOUT_DELAY)) {
                    signalResult(ExperimentResult.SYSTEM_HANG_ASSUMED);
                }
            } catch (final AdbException e1) {
                // do nothing, this branch is taken if the adb hasn't responded
            }
        }
    }

}
