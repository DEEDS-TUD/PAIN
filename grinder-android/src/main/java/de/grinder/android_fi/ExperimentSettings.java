package de.grinder.android_fi;

import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExperimentSettings {

  /** Class level logger */
  static final Logger LOGGER = LoggerFactory.getLogger(ExperimentSettings.class);

  // host tools, full paths
  public final String ADB, ADBSHELL, EMULATOR, GETDELAYS;

  // files on hosts, full paths
  public final String KERNEL_BIMAGE, SYSTEM_IMAGE, SYSTEM_VIMAGE, USERDATA_IMAGE,
      USERDATA_VIMAGE, SDCARD_IMAGE, SDCARD_VIMAGE, WORKLOAD_APK;

  // directories on device, full paths
  public final String AVD_BIN_DIR, AVD_MODULE_DIR;

  // files on device, full paths
  public final String AVD_GRINDER_LKM, AVD_CANDE_LIGHT, AVD_CANDE_HEAVY_LKM,
      AVD_WORKLOAD_FLAGFILE;

  // misc strings
  public final String AVD_NAME, WORKLOAD_ACTIVITY, GRINDER_HOST;

  // misc numbers
  public int GRINDER_PORT;

  // detector constants
  public final long DETECTION_PERIOD, SYSINIT_HANG_ASSUMPTION_DELAY,
      APP_HANG_ASSUMPTION_DELAY, APP_INSTALL_FAIL_DELAY, SYSTEM_HANG_ASSUMPTION_DELAY,
      SYSTEM_HANG_ASSUMPTION_PERIOD, ADB_TIMEOUT_DELAY;

  private static String getSetting(final Map<String, String> prop,
      final Map<String, String> env, final String propertyName,
      final String environmentName) throws SettingsException {
    final String setting = prop.get(propertyName);
    if (setting != null) {
      return setting;
    }
    return getOrThrow(env, environmentName);
  }

  private static String getOrThrow(final Map<String, String> m, final String key)
      throws SettingsException {
    final String tmp = m.get(key); // hack to work with both, env and prop
    if (tmp == null) {
      final String msg = String.format("Cannot find setting: '%s'.", key);
      LOGGER.error(msg);
      throw new SettingsException(msg);
    }
    return tmp;
  }

  private static long getLongOrThrow(final Map<String, String> m, final String key)
      throws SettingsException {
    final String tmp = getOrThrow(m, key);

    try {
      return Long.parseLong(tmp);
    } catch (final NumberFormatException e) {
      throw new SettingsException(String.format("Conversion to number failed for: %s",
          tmp), e);
    }
  }

  public ExperimentSettings(final String propFilePath, Map<String, String> env)
      throws SettingsException {
    if (env == null) {
      env = System.getenv();
    }
    if (!env.containsKey("AFI_HOME")) {
      throw new RuntimeException("Invalid environment setup.");
    }

    Map<String, String> prop;
    String propFileName = propFilePath != null && !propFilePath.isEmpty() ? propFilePath
        : env.get("AFI_GRINDER_PROPERTIES");
    if (propFileName == null) {
      propFileName = "grinder-afi.properties";
    }
    try (FileReader r = new FileReader(propFileName)) {
      final Properties realProp = new Properties();
      realProp.load(r);
      @SuppressWarnings("unchecked")
      final Map<String, String> propMap = (Map) realProp;
      prop = propMap;
      LOGGER.info("Using property file " + propFileName);
    } catch (final IOException e) {
      LOGGER.error("Loading AFI settings failed: %s", e.getMessage());
      throw new SettingsException("Cannot load settings", e);
    }

    String tmp;
    tmp = prop.get("adb");
    if (tmp != null) {
      ADB = tmp;
    } else {
      ADB = getOrThrow(env, "ANDROID_PLATFORM_TOOLS") + "/adb";
    }

    tmp = prop.get("adb_shell");
    if (tmp != null) {
      ADBSHELL = tmp;
    } else {
      ADBSHELL = getOrThrow(env, "AFI_SCRIPT_DIR") + "/adbsh.sh";
    }

    tmp = prop.get("emulator");
    if (tmp != null) {
      EMULATOR = tmp;
    } else {
      EMULATOR = String.format("%s/emulator64-%s", getOrThrow(env, "ANDROID_SDK_TOOLS"),
          getOrThrow(env, "ARCH"));
    }

    GETDELAYS = getSetting(prop, env, "getdelays", "AFI_EXE_GETDELAYS");

    KERNEL_BIMAGE = getSetting(prop, env, "kernel_bimage", "AFI_KERNEL_BIMAGE");

    SYSTEM_IMAGE = getSetting(prop, env, "system_image", "AFI_SYSIMG");

    SYSTEM_VIMAGE = getSetting(prop, env, "system_vanilla_image", "AFI_VSYSIMG");

    USERDATA_IMAGE = getSetting(prop, env, "userdata_image", "AFI_DATIMG");

    USERDATA_VIMAGE = getSetting(prop, env, "userdata_vanilla_image", "AFI_VDATIMG");

    SDCARD_IMAGE = getSetting(prop, env, "sdcard_image", "AFI_SDIMG");

    SDCARD_VIMAGE = getSetting(prop, env, "sdcard_vanilla_image", "AFI_VSDIMG");

    WORKLOAD_APK = getSetting(prop, env, "workload_apk", "AFI_WL_PATH");

    AVD_BIN_DIR = getSetting(prop, env, "avd_bin_dir", "AFI_AVDSYS_BIN_DIR");

    AVD_MODULE_DIR = getSetting(prop, env, "avd_module_dir", "AFI_AVDSYS_MOD_DIR");

    AVD_GRINDER_LKM = String.format("%s/%s.ko", AVD_MODULE_DIR,
        getSetting(prop, env, "grinder_lkm_name", "AFI_GRINDER_LKM_NAME"));

    AVD_CANDE_LIGHT = getSetting(prop, env, "cande_light_name", "AFI_CANDE_LIGHT_NAME");

    AVD_CANDE_HEAVY_LKM = String.format("%s/%s.ko", AVD_MODULE_DIR,
        getSetting(prop, env, "cande_heavy_name", "AFI_CANDE_HEAVY_NAME"));

    AVD_WORKLOAD_FLAGFILE = getSetting(prop, env, "avd_worload_flagfile",
        "AFI_WL_FLAGFILE");

    AVD_NAME = getSetting(prop, env, "avd_name", "AFI_AVD_NAME");

    tmp = prop.get("workload_activity");
    if (tmp != null) {
      WORKLOAD_ACTIVITY = tmp;
    } else {
      WORKLOAD_ACTIVITY = String.format("%s/%s", getOrThrow(env, "AFI_WL_PACK"),
          getOrThrow(env, "AFI_WL_CLASS"));
    }

    GRINDER_HOST = getOrThrow(prop, "grinder_host");
    GRINDER_PORT = (int) getLongOrThrow(prop, "grinder_port");

    DETECTION_PERIOD = getLongOrThrow(prop, "detection_period");
    SYSINIT_HANG_ASSUMPTION_DELAY = getLongOrThrow(prop, "sysinit_hang_assumer_delay");
    APP_HANG_ASSUMPTION_DELAY = getLongOrThrow(prop, "app_hang_assumption_delay");
    APP_INSTALL_FAIL_DELAY = getLongOrThrow(prop, "app_install_fail_delay");
    SYSTEM_HANG_ASSUMPTION_DELAY = getLongOrThrow(prop, "system_hang_assumption_delay");
    SYSTEM_HANG_ASSUMPTION_PERIOD = getLongOrThrow(prop, "system_hang_assumption_period");
    ADB_TIMEOUT_DELAY = getLongOrThrow(prop, "adb_timeout_delay");
  }

  public ExperimentSettings() throws SettingsException {
    this(null, null);
  }

  public static class SettingsException extends Exception {

    private static final long serialVersionUID = -4968956118330770600L;

    public SettingsException() {
      super();
    }

    public SettingsException(final String message, final Throwable cause,
        final boolean enableSuppression, final boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
    }

    public SettingsException(final String message, final Throwable cause) {
      super(message, cause);
    }

    public SettingsException(final String message) {
      super(message);
    }

    public SettingsException(final Throwable cause) {
      super(cause);
    }
  }
}
