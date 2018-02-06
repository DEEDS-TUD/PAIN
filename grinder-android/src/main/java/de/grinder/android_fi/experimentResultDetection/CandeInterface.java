package de.grinder.android_fi.experimentResultDetection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.grinder.android_fi.AndroidDebuggingBridge;
import de.grinder.android_fi.AndroidDebuggingBridge.AdbException;
import de.grinder.android_fi.ExperimentSettings;

public class CandeInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(CandeInterface.class);

    private final AndroidDebuggingBridge adb;

    private final ExperimentSettings settings;

    public CandeInterface(final AndroidDebuggingBridge adb,
                          final ExperimentSettings settings) {
        this.adb = adb;
        this.settings = settings;
    }

    public void startCande() {
        try {
            LOGGER.info("Loading Cande Heavy-Detector");
            adb.loadModule(settings.AVD_CANDE_HEAVY_LKM);
            LOGGER.info("Starting Cande Light-Detector");
            adb.execShell(settings.AVD_CANDE_LIGHT, settings.AVD_BIN_DIR, false);
        } catch (final AdbException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
            // TODO better error handling
        }
    }
}
