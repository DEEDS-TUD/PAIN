# GRINDER for Android FI: Setup Instructions

## Prerequisites

To install and operate GRINDER with Android, the following software needs to be installed.

- Java 7
- Python 3
- Maven
* gcc, g++, make, autoconf (for mutation-based injections)
- MySQL
- pre-built tool chain for cross compiling from [Google](https://android.googlesource.com/platform/prebuilts/gcc/linux-x86/arm/arm-eabi-4.6)
- Android SDK from [Google](https://developer.android.com/sdk/index.html).
- Android NDK from [Google](https://developer.android.com/tools/sdk/ndk/index.html)
* If you're running an amd64 system: all 32-bit libraries needed by the Android SDK. In the case of Ubuntu 13.10, this is libstdc++6:i386 (for mksdcard) and zlib1g:i386 (for something invoked in the workload build). You can check shared lib dependencies of binaries with ldd.

### MySQL configuration

The PAIN experiments are based on the GRINDER fault injection tool. GRINDER has been pre-configured for the experiments. For this pre-configuration to work, a MySQL database `'grinder'` and a user `'grinder'@'localhost'` with password `'grinder'`, who has full access to the `grinder` db, are required.

### Android SDK configuration

Depending on the Android SDK that you intend to work with, install the required components by invoking the `android` binary of the downloaded SDK bundle.

You need at least

* Android SDK tools
* Android SDK Platform-tools
* Android SDK Build-tools
* SDK Platform (for the respective Android version)
* ARM EABI for your target platform (for the respective Android version)
* Google APIs (for the respective Android version)
* Android Support Library

To work on an emulated Android device, you need to build an AVD from SDK `android` tool. Our experiments work with the following configuration:

* Device: 5.1" WVGA (480 x 800: mdpi)
* Target: Android 4.4.2 - API level 19
* CPU/ABI: ARM (armeabi-v7a)
* Skin: WVGA800
* RAM: 512
* VM heap: 16
* SD Card: 20 MiB

When you want to use a different setup, make sure to not use a "Google API" for *Target*. The current build environment is not set up to deal with that setting.

A *CPU/ABI* other than armeabi-v7a was never tested and there is a good chance it won't work.

## Setup

### Update git submodules

After cloning into the *android\_fi* (in the following "AFI\_HOME") repository, the *GRINDER* and *goldfish\_kernel* sub-modules need to be initialized and updated:

	AFI_HOME $ git submodule update --init

If the goldfish\_kernel directory is empty after that, do one of the following
(depending on the kernel version you want to use):

	AFI_HOME/goldfish_kernel $ git checkout origin/android-goldfish-2.6.29
or:
	
	AFI_HOME/goldfish_kernel $ git checkout origin/android-goldfish-3.4
	
### Configure environment for the Android FI (AFI) project:

Most AFI scripts and tools require a certain environment setup to work properly. The needed environment settings are provided by a script, usually `env.sh` in the AFI\_HOME root directory, that must be sourced in your shell. The environment setup script must be generated with `config/envgen.sh`, which auto-detects most settings and asks for others. The script currently assumes that an AVD has been created.

Source the environment setup script using:

	source env.sh

or alternatively:

	. ./env.sh

Note that for some scripts to work properly, the `env.sh` script or a copy MUST be located in AFI\_HOME!

Note that the script generator may miss some settings or get some of them wrong. In this case, you have to manually edit `env.sh`.


### Build Goldfish kernel with LKM support and the target module as LKM:

Ensure that you have sourced the *env.sh* file to have a suitable environment for cross-compilation.

To configure the Goldfish kernel to support loadable kernel modules, you can use one of the kernel config files provided in in the `config` directory.
Depending on the kernel version you target, copy `config/config-2.6` or `config/config-3.4` to `goldfish_kernel` and rename the configuration file to `.config`.

To build the kernel execute

	AFI_HOME/goldfish_kernel $ make

Note: If you want to use the Goldfish MMC driver as module you need to declare a *LICENSE\_MODULE("GPL");* in order to enable it to access the required kernel API. You can apply the *config/gpl\_goldfish.patch* patch to make this change:

	AFI_HOME/goldfish_kernel $ patch -p1 -i ../config/gpl_goldfish.patch

Without the patch the kernel gets tainted when the instrumented goldfish module is loaded. Remember to re-build the *modules* target.

### Prepare instrumentation for injections

We use Roberto Natella's SAFE to generate mutants that are representative for residual software faults. SAFE requires C code to be preprocessed by MCPP.

1. Download and build mcpp

		AFI_HOME/config $ make_mcpp.sh

2. Inject mcpp into the ARM toolchain

		AFI_HOME/config $ sudo install_mcpp.sh

3. Build the targeted lkm(s)

		AFI_HOME/goldfish_kernel $ cp ../config/Makefile.target.3.4 Makefile
		AFI_HOME/goldfish_kernel $ make modules
		AFI_HOME/goldfish_kernel $ cp ../config/Makefile.kernel.3.4 Makefile

4. Upon first usage confirm compliance with SAFE's license

		AFI_HOME/fault_injection_scripts $ ./injection

5. Generate mutants

		    AFI_HOME/fault_injection_scripts $ ./mutant_generation.sh <path-to-targeted-lkm>.c <mutation-degree>
The degree specifies the degree to which mutants are mutated recursively. For higher order mutants, the generation may take considerable time.
You may check the progress via

		    AFI_HOME/fault_injection_scripts $ tail -f mutant_generation.log
The mutants are stored to a directory named after the targeted lkm, which resides in the directory from which the script was started.

6. Recover original C preprocessor in the build chain

	   	AFI_HOME/config $ sudo ./uninstall_mcpp.sh


### Build grinder module

Ensure that your environment setup is correct (see `env.sh` above) and build the GRINDER LKM as follows:

	AFI_HOME/grinder-lkm $ make


### Build CANDE detectors

Ensure that your environment setup is correct (see `env.sh` above) and build the CANDE detectors and dependencies as follows:

1. Build the sysstat tools `sar` and its shared library `sadc`:

   	 	AFI_HOME/sar-android/sysstat-android $ ./android_build.sh
This should configure the sysstat tools for Android using the NDK and compile the two binaries `sar` and `sadc`.

2. Build the CANDE light detector command line tool for Android:

   	 	AFI_HOME/cande/cande_light_detector $ make android
This should produce the Android executable `cande_light_detector`.

3. Build the CANDE heavy detector LKM for Android:

   	 	AFI_HOME/cande/cande_heavy_detector $ make android
This should produce the Android LKM `cande_heavy_detector.ko`


### Build workload

Ensure that your environment setup is correct (see `env.sh` above) and build the workload application package (APK) as follows:

	AFI_HOME/workload $ ndk-build
	AFI_HOME/workload $ ant debug

If this does not work, try `ant clean` at first.

### Build the custom Android userdata and SD card image

Ensure that your environment setup is correct (see `env.sh` above) and build the custom Android userdata image as follows:

	AFI_HOME/scripts $ ./gen-userdata-image.sh

This should generate a custom userdata image (and a vanilla copy of it) that contains the CANDE detectors as well as the GRINDER LKM.
Note that this may only work for Android systems with kernel 3.4 and above (and a recent API level).
Note that the userdata image generation may need some time since the Android emulator is involved.

Build the SD card image as follows:

	AFI_HOME/scripts $ ./gen-sdcard-image.sh

This should generate an empty SD card image and a vanilla copy of it.


### Check if everything works so far

Ensure that your environment setup is correct (see `env.sh` above) and execute:

	AFI_HOME/scripts $ ./start_android.sh

This starts the emulator with your custom built kernel and Android userdata image. Open another console (do not forget `env.sh`) and execute:

	AFI_HOME/scripts $ ./load_modules.sh
	android_fi/scripts $ ./workload.sh
	AFI_HOME/scripts $ ./unload_modules.sh

None of the commands should produce any errors.


### Build getdelays:

GRINDER uses the getdelays tool to gather statistics about the emulator process. To build it, execute:

	AFI_HOME/getdelays $ make

It uses `sudo` in order to set the cap\_net\_admin capability so that getdelays can gather program statistics without requiring root.


### Build GRINDER:

Due to the repository structure and (as of yet) missing plugin architecture, GRINDER for Android FI must be built in three steps to fulfill all dependency requirements.

1. create the necessary dependencies

   	      AFI_HOME/GRINDER $ mvn install -pl de.tu-darmstadt.informatik.deeds:grinder.server.ext.util -am

2. build the Android plugin

 	      AFI_HOME/grinder-android $ mvn install

3. build GRINDER

	      AFI_HOME/GRINDER $ mvn package

The last two steps may require re-execution if changes are made to GRINDER or the Android target abstraction. There is a script to automate these steps (and skip tests to speed up the build) in `AFI_HOME/grinder-android`.

GRINDER can be invoked via

	AFI_HOME/GRINDER $ java -jar client/core/target/grinder.client.core-<version>.jar

or

	AFI_HOME/grinder-android $ ./start-grinder.sh

Note that the AFI specific components of GRINDER rely on a properties file, which contains various settings. This file is usually named `grinder-afi.properties` and must be stored in GRINDER's working directory. See `AFI_HOME/grinder-android/grinder-afi.properties`. This is automatically taken care of if GRINDER is invoked via the shell script mentioned above.

By default GRINDER starts a GUI for target system and campaign administration. If these are set up, automated tests should be run without invoking the GUI. The corresponding steps are described in the following.


### Run tests:

In order to reproduce our experiments with the Android target system, you should start GRINDER once as stated above and close it again to create the required database schema.
To populate the database with our test data,

* switch to one of the experiment setup branches:
  * parallel_mixedWL
  * parallel_pureWL

* create as many clones of your target system configuration as you intend to run in parallel via `AFI_HOME/scripts/setup-avds.sh`. At this point, at most 48 instances can be run in parallel. This is no technical limitation and the number can easily be increased. The current upper bound is solely determined by the number of GRINDER target templates included in the repository for your convenience.

* fill the test and target databases and assign equal amounts of tests to each target (**ATTENTION: this will erase any prior experiment data in the database.** Make sure you dump the DB to a file if it has any valuable contents!):

	   	AFI_HOME/config $ python setup_db.py grinder_android_400testcases.sql <number of target instances> <campaign name of your choice>
To run sequential experiments, configure 1 as the number of target instances. 

* You can run the campaigns by manually starting them from the GRINDER GUI. However, we recommend running them from the command line:

        AFI_HOME/grinder-android $ ./start-grinder.sh --no-gui <campaign name you chose for the DB setup> android_fi

* To extract result data, run

        AFI_HOME/db-scripts $ python analyzeResults.py

## Contact

PAIN and GRINDER have been developed in the context of research projects. Their documentation is incomplete and their implementation has deficiencies, some of which we are aware of. In case you encounter any problems with the steps above or if you are interested in contributing to PAIN or GRINDER, please contact us via <pain@deeds.informatik.tu-darmstadt.de>.
