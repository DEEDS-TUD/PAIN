package de.grinder.android_fi;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.grinder.android_fi.AndroidDebuggingBridge.AdbException;
import de.grinder.android_fi.Emulator.EmulatorException;
import de.grinder.android_fi.ExperimentSettings.SettingsException;
import de.grinder.android_fi.experimentResultDetection.ExperimentResult;
import de.grinder.util.cue.CUEAbstraction;

/**
 * Twin CUE abstraction for {@link EmulatedAndroid} that collects detector calibration
 * data.
 * <p>
 * There can exist only one instance of this class. Executing multiple calibrations in
 * parallel is not supported since the calibration target itself takes care of using
 * parallel emulator instances.
 * <p>
 * This CUE abstraction can only be used to execute a single test case, which must be a
 * calibration test case. A calibration test case must specify the following data:
 * <ul>
 * <li>List of numbers that specifies the amount P of parallel emulators to use. One
 * calibration run with N repetitions is executed per specified number. The list is
 * expected in the {@code kservice} database column. The expected format is a comma
 * separated list of number and number ranges, e.g., {@code "1,2,3,4"}, {@code "1-4"} and
 * {@code "1-2,3-4"} result in the same calibration runs.</li>
 * <li>Number N of repetitions per calibration run. N is expected in the {@code parameter}
 * database column.</li>
 * <li>Name (file name) of original kernel module to use during calibration run. The name
 * is expected in the {@code module} database column.</li>
 * </ul>
 * <p>
 * For each specified parallelism level P, N runs with P parallel emulators are executed.
 * The individual results of each single run is written to a local CSV file and logged to
 * the database. No further data processing is performed. Note that a run has failed if at
 * least one of the P emulators encountered an error. In such cases, a run with P
 * emulators can be repeated up to R times (currently 3), i.e., a run with P emulators can
 * require at max N+R repetitions. The calibration fails if for at least one P less than N
 * valid result sets are produced. In most cases, the failure of a calibration run with P
 * emulators is detected if at least one of the emulators does not finish its execution in
 * time. Currently, a timeout value of 30 minutes is used as a safety measure.
 */
public class EmulatedAndroidCalib implements CUEAbstraction {

    /** Safety flag for enforcing that there can exist only one instance of this class. */
    private static boolean _exists = false;

    /**
     * Maximum time in minutes to wait for all calibration threads to finish during a
     * calibration run with one or more parallel emulators.
     */
    private static long CALIBRATION_RUN_TIMEOUT = 30;

    /** Class level logger. */
    private static final Logger LOG = LoggerFactory.getLogger(EmulatedAndroidCalib.class);

    /** File name of local result CSV file. */
    private static final String RESULT_OUTPUT_FILE = "calibration-results.txt";

    /** Safety flag for enforcing that only one test case can be executed for calibration. */
    private boolean _executedOnce = false;

    /** Stores all emulator instances. */
    private final List<Emulator> emus = new ArrayList<>();

    /** Executor service for executing all the calibration experiment runs. */
    private ExecutorService executor;

    /** Proxy for communication with GRINDER. */
    private final AndroidGrinderProxy grinder;

    /** Writer for the result CSV file. */
    private FileWriter resultOutput;

    /** Settings properties for system dependent tools and paths. */
    private final ExperimentSettings settings;

    /**
     * Constructs an emulated Android calibration object that controls the calibration. Note
     * only one object of this class can be constructed.
     *
     * @throws RuntimeException
     *           If an attempt is made to construct more than one object of this class or if
     *           the experiment setting could not be loaded.
     */
    public EmulatedAndroidCalib() {
        // safety measure: ensure only one instance of this class can be created!
        if (_exists) {
            LOG.error("Only one instance of this class is allowed to exist");
            throw new RuntimeException(
                "The only allowed instance of this class was already created.");
        } else {
            _exists = true;
        }

        try {
            settings = new ExperimentSettings();
        } catch (final SettingsException e) {
            LOG.error("Failed to load experiment settings: {}", e.getMessage(), e);
            throw new RuntimeException("Experiment settings load failure", e);
        }

        grinder = new AndroidGrinderProxy(settings.GRINDER_HOST, settings.GRINDER_PORT);
    }

    /**
     * Cancels all calibration threads and the associated emulators.
     *
     * The calibration threads are canceled through the {@link Future} interface. Canceling
     * the {@link Future} object of a single calibration run should not only terminate the
     * run, but also cleanly shutdown the associated emulator instance.
     * <p>
     * This method blocks and waits until all emulators signaled that the shutdown
     * completded.
     *
     * @param futures
     *          The list of {@link Future} objects associated to the calibration threads
     *          that should be canceled.
     */
    private void cancelAll(final List<Future<CalibrationResult>> futures) {
        LOG.info(String.format("Canceling started emulator runs: %d", futures.size()));
        int i = 0;
        for (final Future<?> fut : futures) {
            LOG.warn(String.format("Cancel future<%d>: start", i));
            final boolean ret = fut.cancel(true);
            LOG.warn(String.format("Cancel future<%d>: done: ret=%b, canceled=%b, done=%b", i,
                                   ret, fut.isCancelled(), fut.isDone()));
            ++i;
        }
        for (int j = 0; j < futures.size(); ++j) {
            try {
                emus.get(j).awaitShutdown();
                LOG.debug(String.format("Emulator %d shutdown complete.", j));
            } catch (final EmulatorException e) {
                LOG.error(
                    String.format("Failed to wait for shutdown of emu %d: %s", j, e.getMessage()),
                    e);
            }
        }
        LOG.info("Completed emulator canceling.");
    }

    /**
     * Ensures that only one test case can be executed with the single instance of this
     * class. This is a safety measure as only one test case is supported.
     *
     * @return {@code True} if this is the first test case execution, i.e., this method is
     *         called for the first time. Otherwise, returns {@code false} and signals an
     *         experiment error.
     */
    private boolean ensureOnce() {
        if (_executedOnce) {
            LOG.error("Attempt to execute multiple test cases for calibration. "
                      + "This is currently not supported.");
            try {
                grinder.sendLog("ERROR: Multiple test cases for calibration not supported.");
            } catch (final IOException e) {
                LOG.error("Network communication failed.", e);
            }
            signalFailure();
            return false;
        }
        _executedOnce = true;
        return true;
    }

    /**
     * Executes an actual calibration run for the specified number of parallel emulator
     * instances.
     *
     * @param numEmus
     *          Number of parallel emulators.
     * @param runId
     *          The current run ID, i.e., the current run repetition number.
     * @param moduleName
     *          The kernel modules to load during the run.
     * @return A list of the individual calibration results for each started emulator.
     * @throws CalibrationException
     *           If something goes wrong.
     */
    private List<CalibrationResult> execCalibRun(final int numEmus, final int runId,
            final String moduleName) throws CalibrationException {
        LOG.info(String.format("Starting calibration with %d emulators.", numEmus));

        final Semaphore sem = new Semaphore(-(numEmus - 1));
        final List<Future<CalibrationResult>> calibFutures = new ArrayList<>(numEmus);
        for (int i = 0; i < numEmus; ++i) {
            calibFutures.add(executor.submit(new CalibrationRun(numEmus, runId, emus.get(i),
                                             moduleName, sem)));
        }
        try {
            waitForCalibrationThreads(sem, numEmus);
        } catch (final CalibrationException e) {
            LOG.info("Canceling all calibration threads.");
            cancelAll(calibFutures);
            throw e;
        }
        return getSingleRunResults(calibFutures);
    }

    /**
     * Retrieves the calibration configuration from GRINDER.
     *
     * @return The calibration configuration.
     * @throws CalibrationException
     *           If something goes wrong, e.g., communication with GRINDER or invalid data.
     */
    private CalibConfig getCalibConfig() throws CalibrationException {
        try {
            return CalibConfig.fromDbConfigString(grinder.getConfiguration());
        } catch (final Exception e) {
            final String msg = "Failed to retrieve calibration configuration.";
            LOG.error(msg, e);
            throw new CalibrationException(msg, e);
        }
    }

    /**
     * Retrieves the list of calibration results for the given list of {@link Future}
     * objects that are associated to the single calibration runs.
     *
     * @param calibFutures
     *          The {@link Future} objects from which to get calibration results.
     * @return The list of calibration results.
     * @throws CalibrationException
     *           If some thing goes wrong, e.g., a run that had an exception is encountered.
     */
    private List<CalibrationResult> getSingleRunResults(
        final List<Future<CalibrationResult>> calibFutures) throws CalibrationException {
        final int numEmus = calibFutures.size();
        final List<CalibrationResult> runResults = new ArrayList<>(numEmus);

        try {
            for (final Future<CalibrationResult> resultFuture : calibFutures) {
                runResults.add(resultFuture.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            final String msg = String.format(
                                   "Failed to retrieve result for calibration run with %d emulators.", numEmus);
            LOG.error(msg, e);
            throw new CalibrationException(msg, e);
        }

        return runResults;
    }

    /**
     * Initializes the local CSV result file. The file is opened in append mode if it
     * already exists. The CSV header is written.
     *
     * @throws IOException
     *           If file I/O failed.
     */
    private void initCsvFile() throws IOException {
        resultOutput = new FileWriter(RESULT_OUTPUT_FILE, true);
        resultOutput.write(CalibrationResult.csvHeadLine());
    }

    /**
     * Initializes the DB result log by adding the CSV header.
     *
     * @throws CalibrationException
     *           If communication with GRINDER fails.
     */
    private void initResultLog() throws CalibrationException {
        try {
            grinder.sendLog(CalibrationResult.csvHeadLine());
        } catch (final IOException e) {
            LOG.error("Init result log: network communication failed.", e);
            throw new CalibrationException("Failed to init result log.", e);
        }
    }

    /**
     * Prepares for running calibrations with the given maximum number of emulators.
     * Emulator instances and a thread pool are pre-allocated.
     *
     * @param maxEmus
     *          The maximum number of emulators to prepare for.
     */
    private void prepareEmulators(final int maxEmus) {
        LOG.info(String.format("Preparing %d emulator instances for calibration", maxEmus));

        final EmulatorFactory factory = EmulatorFactory.getInstance();
        for (int i = 0; i < maxEmus; ++i) {
            emus.add(factory.getNewEmu(settings));
        }
        executor = Executors.newFixedThreadPool(maxEmus);
    }

    /**
     * Signal experiment failure to GRINDER.
     */
    private void signalFailure() {
        try {
            LOG.info("Calibration run failed.");
            grinder.sendExperimentFinished(ExperimentResult.EXPERIMENT_FAILURE.getId());
        } catch (final IOException e) {
            LOG.error("Failed to signal calibration failure to GRINDER.", e);
        }
    }

    /**
     * Signal successful experiment finish to GRINDER.
     */
    private void signalFinished() {
        try {
            LOG.info("Calibration run finished.");
            grinder.sendExperimentFinished(ExperimentResult.FINISHED.getId());
        } catch (final IOException e) {
            LOG.error("Failed to signal calibration end to GRINDER.", e);
        }
    }

    /**
     * Waits until all calibration threads have signaled that they have finished.
     *
     * @param sem
     *          Semaphore to use for synchronization (waiting).
     * @param numEmus
     *          The number of parallel emulators.
     * @throws CalibrationException
     *           If something goes wrong.
     */
    private void waitForCalibrationThreads(final Semaphore sem, final int numEmus)
    throws CalibrationException {
        LOG.debug(String.format("Waiting until %d calibration threads have finished.",
                                numEmus));
        try {
            if (!sem.tryAcquire(CALIBRATION_RUN_TIMEOUT, TimeUnit.MINUTES)) {
                final String msg = String.format(
                                       "Failed to wait for %d calibration threads. Timed out.", numEmus);
                LOG.error(msg);
                throw new CalibrationException(msg);
            }
        } catch (final InterruptedException e) {
            final String msg = String.format(
                                   "Failed to wait for %d calibration threads. Interrupted.", numEmus);
            LOG.error(msg, e);
            throw new CalibrationException(msg, e);
        }
        LOG.debug(String.format("All %d calibration threads have finished.", numEmus));
    }

    /**
     * Converts the given results to their CSV representation and writes it to the local
     * file as well as the GRINDER DB.
     *
     * @param results
     *          The results to write.
     * @throws CalibrationException
     *           If something goes wrong, e.g., GRINDER communication fails.
     */
    private void writeAndLogResults(final List<CalibrationResult> results)
    throws CalibrationException {
        final StringBuilder sb = new StringBuilder();
        for (final CalibrationResult r : results) {
            sb.append(r.toCsvLine());
        }
        final String resultStr = sb.toString();
        try {
            resultOutput.write(resultStr);
            resultOutput.flush();
            grinder.sendLog(resultStr);
        } catch (final IOException e) {
            final String msg = "Failed to write calibration results to file/DB.";
            LOG.error(msg, e);
            throw new CalibrationException(msg, e);
        }
    }

    @Override
    public void reset() {
        LOG.info("Resetting Emulated Android Calibration target.");

        if (!executor.isShutdown()) {
            LOG.info("Shutting down executor service.");
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    LOG.error("Failed to shutdown executor service in time.");
                } else {
                    LOG.debug("Executor service shutdown complete.");
                }
            } catch (final InterruptedException e) {
                LOG.error("Failed to shutdown executor service. Interrupted.", e);
            }
        }

        try {
            resultOutput.close();
        } catch (final IOException e) {
            LOG.error(
                String.format("Failed to close calibration times log file: %s", e.getMessage()),
                e);
        }
    }

    @Override
    public void runExperiment() {
        LOG.info("Running calibration.");
        if (!ensureOnce()) {
            return;
        }

        try {
            final CalibConfig config = getCalibConfig();
            LOG.info(String.format("Calibration configuration in use: %s", config));

            prepareEmulators(config.getMaxNumEmus());
            initResultLog();

            // Note: We store all individual results in here in case we want to do further
            // processing in the future.
            final List<List<List<CalibrationResult>>> calibResults = new ArrayList<>(
                config.numEmuList.size());
            // one calibration run per configured parallelism level
            for (final int curNumEmus : config.numEmuList) {
                final List<List<CalibrationResult>> emuResults = new ArrayList<>(
                    config.numRepeats);
                int numFailRetries = 0;
                final int maxRetries = 3;
                // repeat single run configured number of times
                for (int i = 0; i < config.numRepeats; ++i) {
                    LOG.info(String.format(
                                 "Starting calibration run: %d parallel emus, repetition %d%s",
                                 curNumEmus,
                                 i + 1,
                                 numFailRetries > 0 ? String.format(", failed retries: %d (max: %d)",
                                         numFailRetries, maxRetries) : ""));
                    try {
                        final List<CalibrationResult> curResults = execCalibRun(curNumEmus, i,
                                config.moduleName);
                        emuResults.add(curResults);
                        LOG.debug("Finished calibration run.");
                        for (final CalibrationResult r : curResults) {
                            LOG.debug(r.toString());
                        }
                        writeAndLogResults(curResults);
                    } catch (final CalibrationException e) {
                        if (numFailRetries < maxRetries) {
                            ++numFailRetries;
                            --i;
                        } else {
                            LOG.error(String.format("Run failed! No remaining retries: %d.",
                                                    numFailRetries));
                            throw e;
                        }
                    }
                }
                calibResults.add(emuResults);
            }

            LOG.info("Finished all calibration runs.");

        } catch (final CalibrationException e) {
            LOG.error("Calibration failed.", e);
            signalFailure();
            return;
        } catch (final Throwable t) {
            LOG.error("Enexpected exception occurred during calibration logic.", t);
            signalFailure();
            return;
        }

        // all fine if we reach this point
        signalFinished();
    }

    @Override
    public void start() {
        LOG.info("Starting Emulated Android Calibration target.");
        try {
            grinder.connect();
        } catch (final IOException e) {
            LOG.error("Failed to connect to GRINDER.", e);
            throw new RuntimeException("Connection to GRINDER failed", e);
        }

        try {
            initCsvFile();
        } catch (final IOException e) {
            final String msg = "Failed to initialize calibration results file.";
            LOG.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    @Override
    public void stop() {
        LOG.info("Stopping Emulated Android Calibration target.");
        try {
            grinder.disconnect();
        } catch (final IOException e) {
            // we're good
        }
    }

    /**
     * Represents the calibration configuration as read from the database.
     */
    private static class CalibConfig {

        /**
         * Module to use during calibration. Read from 'module' column in test case database
         * table.
         */
        public final String moduleName;

        /**
         * Listing of emulator counts to calibrate for. Read from 'kservice' column in test
         * case database table. Each entry in this list results in one calibration run, which
         * consists of repeated single calibration runs with the specified amount of
         * emulators.
         */
        public final List<Integer> numEmuList;

        /**
         * The number of repetitions to execute to gain more confidence in results. Read from
         * 'parameter' column in test case database table.
         */
        public final int numRepeats;

        /**
         * Produces a list with all integers in the specified range, including borders.
         *
         * @param start
         *          First integer in the range.
         * @param end
         *          Last integer in the range.
         * @return The produce integer list.
         */
        private static List<Integer> genRange(int start, int end) {
            if (start > end) {
                final int tmp = end;
                end = start;
                start = tmp;
            }

            final List<Integer> lst = new ArrayList<>((end - start) + 1);
            for (int i = start; i <= end; ++i) {
                lst.add(i);
            }
            return lst;
        }

        /**
         * Parses number and number range string into a list of integers.
         * <p>
         * Example: input string: {@code "1,2,5-8"} => output list: {@code [1, 2, 5, 6, 7, 8]}
         * <p>
         * Note the the result list contains no double entries and the integers are sorted
         * ascending.
         *
         * @param listStr
         *          String to parse.
         * @return A list with one integer for each literal integer or integer in ranges given
         *         by the input string.
         * @throws CalibrationException
         *           If anything goes wrong.
         */
        private static List<Integer> parseNumEmuList(final String listStr)
        throws CalibrationException {
            final String[] rawElements = listStr.split(",");
            final List<Integer> list = new ArrayList<>();

            try {
                for (final String e : rawElements) {
                    if (e.contains("-")) {
                        final String[] range = e.split("-");
                        if (range.length != 2) {
                            throw new NumberFormatException(String.format("Invalid range: %s", e));
                        }
                        final int start = Integer.parseInt(range[0]);
                        final int end = Integer.parseInt(range[1]);
                        list.addAll(genRange(start, end));
                    } else {
                        list.add(Integer.valueOf(e));
                    }
                }

                // remove doubles and sort ascending
                final HashSet<Integer> tmpSet = new HashSet<>(list);
                list.clear();
                list.addAll(tmpSet);
                Collections.sort(list);
            } catch (final Exception e) {
                final String msg = String.format("Failed to parse emulator numbers list: %s",
                                                 e.getMessage());
                LOG.error(msg, e);
                throw new CalibrationException(msg, e);
            }

            return list;
        }

        /**
         * Construct a calibration configuration object from the provided raw configuration
         * string as sent by GRINDER.
         *
         * @param rawConfig
         *          The raw config string.
         * @return A calibration configuration object according to the provided config string.
         * @throws CalibrationException
         *           If anything goes wrong.
         */
        public static CalibConfig fromDbConfigString(final String rawConfig)
        throws CalibrationException {
            final String[] config = rawConfig.split(" ");
            final String moduleName = config[0];
            final List<Integer> numEmuList = parseNumEmuList(config[1]);
            int numRepeats;
            try {
                numRepeats = Integer.parseInt(config[2]);
            } catch (final NumberFormatException e) {
                throw new CalibrationException("Failed to parse number of repetitions.", e);
            }
            return new CalibConfig(moduleName, numEmuList, numRepeats);
        }

        /**
         * Constructs a calibration configuration object with specified settings.
         *
         * @param moduleName
         *          Module for calibration.
         * @param numEmuList
         *          Number of emulators to use for calibration runs.
         * @param numRepeats
         *          Number of repetitions to execute.
         */
        public CalibConfig(final String moduleName, final List<Integer> numEmuList,
                           final int numRepeats) {
            this.moduleName = moduleName;
            this.numEmuList = numEmuList;
            this.numRepeats = numRepeats;
        }

        /**
         * Retrieves the maximum number of emulators needed for calibration runs.
         *
         * @return The maximum emulator count.
         */
        public int getMaxNumEmus() {
            return numEmuList.get(numEmuList.size() - 1);
        }

        @Override
        public String toString() {
            return String.format("moduleName=%s, repeats=%d, numEmus=%s", moduleName,
                                 numRepeats, numEmuList);
        }
    }

    /**
     * Represents a calibration result.
     *
     * This class is used to represent the results of a single calibration run.
     */
    private static class CalibrationResult {

        /** Emulator ID, i.e., which emulator produced this result. */
        public final int emuId;

        /** Parallelism level, i.e., number of emulator instances run in parallel. */
        public final int pLevel;

        /** Run ID, i.e., in which run (repetition) was this result produced. */
        public final int runId;

        /** The time in milliseconds needed to finish the emulator system initialization. */
        public final long sysinitTime;

        /**
         * The time in milliseconds needed to complete the workload, i.e. time between
         * workload start and finish signals.
         */
        public final long workloadTime;

        /**
         * Retrieves a suitable CSV header line for CSV representations of results.
         *
         * @return A string containing a suitable CSV header line. The returned string
         *         contains a terminating newline character.
         */
        public static String csvHeadLine() {
            return "P-Level,Run,Emu,SysInit,Workload\n";
        }

        /**
         * Constructs a calibration result object with specified management data and measured
         * time values.
         *
         * @param pLevel
         *          Parallelism level.
         * @param runId
         *          Run identifier.
         * @param emuId
         *          Emulator identifier.
         * @param sysinitTime
         *          The measured or computed system initialization duration.
         * @param workloadTime
         *          The measured or computed workload execution duration.
         */
        public CalibrationResult(final int pLevel, final int runId, final int emuId,
                                 final long sysinitTime, final long workloadTime) {
            this.pLevel = pLevel;
            this.runId = runId;
            this.emuId = emuId;
            this.sysinitTime = sysinitTime;
            this.workloadTime = workloadTime;
        }

        /**
         * Converts the result into a CSV string representation.
         *
         * @return A string representing this result as line in CSV format. The returned
         *         string contains a terminating newline character.
         */
        public String toCsvLine() {
            return String.format("%d,%d,%d,%d,%d\n", pLevel, runId, emuId, sysinitTime,
                                 workloadTime);
        }

        @Override
        public String toString() {
            return String.format("P: %d, R: %d, Emu: %d, SysInit: %d ms, Workload: %d ms",
                                 pLevel, runId, emuId, sysinitTime, workloadTime);
        }

    }

    /**
     * Represents and controls one single calibration run for one specified emulator
     * instance.
     * <p>
     * This class is not thread-safe beyond the semantics of the {@link Callable} interface.
     */
    private class CalibrationRun implements Callable<CalibrationResult> {

        /** ADB object for controlling the associated emulator instance. */
        private final AndroidDebuggingBridge adb;

        /** Emulator used for this calibration run. */
        private final Emulator emu;

        /** Instance level logger. */
        private final Logger LOG;

        /**
         * Kernel module (file name) to load during calibration run. Should be the original
         * module version without added faults.
         */
        private final String moduleName;

        /** Parallelism level used in this run. */
        private final int pLevel;

        /** ID of this run, i.e., number of the current run repetition. */
        private final int runId;

        /** Semaphore for synchronizing all parallel emulator instances. */
        private final Semaphore sem;

        /**
         * Constructs a calibration run object that controls a single calibration run for one
         * emulator instance.
         *
         * @param pLevel
         *          Overall parallelism level used in this run.
         * @param runId
         *          ID of this run.
         * @param emu
         *          Emulator instance to use for this single run
         * @param moduleName
         *          Kernel module (file name) to load during the single run.
         * @param sem
         *          Semaphore to use for synchronizing with the overall calibration logic.
         */
        public CalibrationRun(final int pLevel, final int runId, final Emulator emu,
                              final String moduleName, final Semaphore sem) {
            this.pLevel = pLevel;
            this.runId = runId;
            this.emu = emu;
            this.moduleName = moduleName;
            this.sem = sem;
            adb = emu.getAdb();
            LOG = LoggerFactory.getLogger(String.format("%s<%s>",
                                          CalibrationRun.class.getName(), emu.getConfig().getId()));
        }

        /**
         * Cleanly shuts down the associated running emulator.
         *
         * @throws CalibrationException
         *           If something goes wrong during the emulator shutdown.
         */
        private void shutdown() throws CalibrationException {
            LOG.info("Shutting down emulator.");
            adb.cancelCreatedProcesses();
            try {
                emu.kill();
            } catch (final EmulatorException e) {
                LOG.error(String.format("Failed to stop emulator: %s", e.getMessage()), e);
                throw new CalibrationException("Failed to properly shutdown calibration run", e);
            }
            LOG.debug("Emulator shutdown complete.");
        }

        @Override
        public CalibrationResult call() throws Exception {
            LOG.info("Doing single calibration run.");

            long sysinitStart = 0l;
            long sysinitStop = 0l;
            long workloadStart = 0l;
            long workloadStop = 0l;

            try {
                LOG.info("Starting regular experiment logic.");
                sysinitStart = System.currentTimeMillis();
                try {
                    emu.start();
                } catch (final EmulatorException e) {
                    throw new CalibrationException("Failed to start emulator.", e);
                }

                LOG.info("Waiting for emulator boot-up");
                if (!adb.waitForDeviceChecked(emu)) {
                    LOG.error("Emulator died while waiting for it.");
                    throw new CalibrationException("Emulator died prematurely.");
                }
                LOG.info("Removing external detector flag file");
                adb.removeFile(settings.AVD_WORKLOAD_FLAGFILE, true);

                LOG.info("Loading kernel modules");
                adb.loadModule(String.format("%s/%s", settings.AVD_MODULE_DIR, moduleName));

                // wait for workload start
                LOG.info("Waiting for workload start.");
                adb.waitForLogcatEvent("main", "workload_started", "Workload:D");
                sysinitStop = System.currentTimeMillis();
                workloadStart = sysinitStop;
                LOG.info("Workload started.");

                // wait for workload end or failure
                LOG.info("Waiting for workload end.");
                adb.waitForLogcatEvent("main", "workload_finished|workload_failed", "Workload:D");
                workloadStop = System.currentTimeMillis();

                LOG.info("Finished regular experiment logic.");
            } catch (final AdbException e) {
                LOG.error(String.format("ADB failure in experiment logic: %s", e.getMessage()), e);
                throw new CalibrationException("ADB failure in experiment logic.", e);
            } catch (final CalibrationException e) {
                LOG.error(String.format("Experiment logic signaled failure: %s", e.getMessage()),
                          e);
                throw e;
            } catch (final Throwable t) {
                LOG.error(
                    String.format("Unexpected error in experiment logic: %s", t.getMessage()), t);
                throw new CalibrationException("Unexpected error in experiment logic.", t);
            } finally {
                try {
                    shutdown();
                } catch (final Exception e) {
                    LOG.error("Fatal exception during emulator shutdown", e);
                    throw e;
                } finally {
                    sem.release();
                }
            }

            LOG.info("Finished single calibration run.");
            return new CalibrationResult(pLevel, runId, emu.getConfig().getId(), sysinitStop
                                         - sysinitStart, workloadStop - workloadStart);
        }

    }

    /**
     * Exception class for signaling errors during the calibration process.
     */
    public static class CalibrationException extends Exception {

        private static final long serialVersionUID = -380878107655056011L;

        public CalibrationException() {
            super();
        }

        public CalibrationException(final String message) {
            super(message);
        }

        public CalibrationException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public CalibrationException(final String message, final Throwable cause,
                                    final boolean enableSuppression, final boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }

        public CalibrationException(final Throwable cause) {
            super(cause);
        }

    }

}
