#!/bin/sh
#
# Environment setup script generator.
# First, all needed system specific paths are collected. Then, a config script
# is written to OUT_SCRIPT. The generated scripts exports a number of relevant
# paths and sets up the PATH and other environment variables that are typically
# needed for working with and building the Android FI project.
#
# Invoke with default settings:
#   envgen.sh
#
# Invoke with custom output script path:
#   OUT_SCRIPT="<path>" envgen.sh
#


THIS_SCRIPT="$0"
OUT_SCRIPT=${OUT_SCRIPT-"env.sh"}
OUT_MAKEFILE="Makefile.env"
ARM_GCC_PREFIX="arm-eabi-"
INVALID_PATH="<invalid path>"
SKIPPED_PATH="<TODO: enter manually>"


# TODO
# also see script gen-system-image.sh
AFI_CANDE_LIGHT_DIR="" # light detector path
AFI_CANDE_HEAVY_DIR="" # heavy detector path


# makes sure that an empty path is replaced by an invalid path token; prevents a
# number of silent errors
# ---------------------------------------------------------
invpath() {
  if [ -z "$*" ]
  then
    echo "$INVALID_PATH"
  else
    echo "$*"
  fi
}
# ---------------------------------------------------------

# a wrapper for the dirname command which returns an invalid path instead of
# "." when the empty string is passed as argument; this prevents a number of
# silent errors; results to stdout
# ---------------------------------------------------------
save_dirname() {
  local TMP="$*"
  if [ -z "$TMP" ]
  then
    echo "$INVALID_PATH"
  else
    echo "$(dirname "$TMP")"
  fi
}
# ---------------------------------------------------------

# converts a given path to a canonical absolute path or an invalid path on
# errors; results to stdout
# ---------------------------------------------------------
to_abs_dir() {
  local TMP=$(readlink -e "$*")
  echo "$(invpath "$TMP")"
}
# ---------------------------------------------------------

# evaluate arguments; used for resolving paths containing variables; results to
# stdout
# ---------------------------------------------------------
ev() {
  eval "echo \"$*\""
}
# ---------------------------------------------------------

# double evaluate arguments; used for resolving paths containing variables that
# contain variables; results to stdout
# ---------------------------------------------------------
ev2() {
  ev "$(ev "$*")"
}
# ---------------------------------------------------------

# iterates over given variable names; writes the names and the contents to
# stdout; contents must be directory paths which are tested for existence;
# returns number of encountered invalid paths
# ---------------------------------------------------------
check_paths_in_vars() {
  err=0
  for NAM in "$@"
  do
    CONT="$(ev2 \$$NAM)"
    printf %s "$NAM = $CONT  "
    if [ -d "$(ev2 $CONT)" ];
    then
      echo "(ok)"
    else
      echo "(fail)"
      err=$((err + 1))
    fi
  done
  return $err
}
# ---------------------------------------------------------

# same as above but without writing to stdout
# ---------------------------------------------------------
check_paths_in_vars_silent() {
  err=0
  for NAM in "$@"
  do
    CONT=$(ev2 \$$NAM)
    [ ! -d "$(ev2 $CONT)" ] && err=$((err + 1))
  done
  return $err
}

# ---------------------------------------------------------

# iterates over given variable names; writes the names and the contents to
# stdout; contents must be file paths which are tested for existence;
# returns number of encountered invalid paths
# ---------------------------------------------------------
check_files_in_vars() {
  err=0
  for NAM in "$@"
  do
    CONT=$(ev2 \$$NAM)
    printf %s "$NAM = $CONT  "
    if [ -f "$(ev2 $CONT)" ]
    then
      echo "(ok)"
    else
      echo "(fail)"
      err=$((err + 1))
    fi
  done
  return $err
}
# ---------------------------------------------------------

# selects entries from LST with trailing END; removes trailing END from selected
# entries; results to stdout
# END <- $1; LST <- $*
# ---------------------------------------------------------
filter_and_strip_trailing() {
  local END="$1"
  shift
  local LST="$*"

  local MATCHES=""
  for CUR in $LST
  do
    PAT=".*${END}"
    echo "$CUR"|grep -q "$PAT"
    if [ $? -eq 0 ]
    then
      MATCHES="$MATCHES '${CUR%$END}'"
    fi
  done

  echo "$MATCHES"
}
# ---------------------------------------------------------

# displays the list, each entry is prepended by a number
# LST <- $*
display_list() {
  local count=1
  while [ $# -ne 0 ]
  do
    echo "$count) $1"
    shift
    count=$((count + 1))
  done
}

# selects one entry from LST; prompting with TITLE if LST has more than one
# entry; results to stdout
# TITLE <- $1; LST <- $*
# ---------------------------------------------------------
select_entry() {
  local TITLE="$1"
  shift
  if [ $# -le 1 ]
  then
    REPLY="$*"
  else
    echo "$TITLE"
    PS3="Please select: "
    local LIST
    while [ $# -ne 0 ]
    do
      LIST="$LIST '$1'"
      shift
    done
    set -- $LIST
    while true
    do
      eval display_list $LIST
      printf "$PS3"
      read REPLY
      if [ "$REPLY" -gt 0 ] 2> /dev/null && [ "$REPLY" -le $# ]
      then
        shift $((REPLY - 1))
        eval REPLY=$1
        break
      else
        echo "Invalid selection. Please try again!"
        continue
      fi
    done
  fi
}

# ---------------------------------------------------------

# a quick detect in case the Android stuff is already in PATH; sets SDK, NDK and
# ARM GCC homes
# ---------------------------------------------------------
detect_android_env_quick() {
  echo "Attempting quick detect of Android environment..."

  [ ! -d "$ANDROID_HOME" ] && \
    ANDROID_HOME="$(to_abs_dir "$(save_dirname "$(which android)")")"

  ANDROID_NDK_HOME="$(to_abs_dir "$(save_dirname "$(which ndk-build)")")"
  local ANDROID_ARM_BUILD_BIN="$(to_abs_dir \
    "$(save_dirname "$(which "${ARM_GCC_PREFIX}gcc")")")"
  ANDROID_ARM_BUILD_HOME="$(to_abs_dir "$ANDROID_ARM_BUILD_BIN/..")"
}
# ---------------------------------------------------------

# do a full search for characteristic Android executables; start search at DIR;
# sets SDK, NDK and ARM GCC homes
# DIR <- $*
# ---------------------------------------------------------
detect_android_env_search() {
  local DIR="$(to_abs_dir "$*")"
  echo "Searching for Android directories in [$DIR]..."

  # the magic find that searches for certain executable files that are
  # characteristic for certain Android dev directories
  local PATHS="$(find "$DIR" \( -path "/etc" -or \
    -path "/dev" -or \
    -path "/sys" -or \
    -path "/proc" -or \
    -path "/tmp" -or \
    -path "/boot" -or \
    -path "/root" -or \
    -path "/var" \) \
    -prune -or \
    \( \
    \( -path "*/tools/android" -executable \) -or \
    \( -name "ndk-build" -executable \) -or \
    \( -path "*/bin/arm-eabi-gcc" -executable \) \
    \) \
    -and -exec echo "'{}'" \; \
    2> /dev/null
  )"

  local LST_SDK
  local LST_NDK
  local LST_ARM_GCC

  LST_SDK=$(eval filter_and_strip_trailing "/tools/android" $PATHS)
  LST_NDK=$(eval filter_and_strip_trailing "/ndk-build" $PATHS)
  LST_ARM_GCC=$(eval filter_and_strip_trailing "/arm-eabi-gcc" $PATHS)

  eval select_entry "'Multiple possible Android SDK locations.'" $LST_SDK
  ANDROID_HOME="$(invpath "$REPLY")"
  eval select_entry "'Multiple possible Android NDK locations.'" $LST_NDK
  ANDROID_NDK_HOME="$(invpath "$REPLY")"
  eval select_entry "'Multiple possible ARM GCC locations.'" $LST_ARM_GCC
  local ANDROID_ARM_BUILD_BIN="$(invpath "$REPLY")"
  ANDROID_ARM_BUILD_HOME="$(to_abs_dir "$ANDROID_ARM_BUILD_BIN/..")"
}
# ---------------------------------------------------------

# leave any already valid paths as they are and use a todo tag instead of actual
# paths for the rest; sets SDK, NDK and ARM GCC homes
# ---------------------------------------------------------
detect_android_env_manual() {
  echo "Android SDK path:"
  while true
  do
    read ANDROID_HOME
    if [ -x "$ANDROID_HOME/tools/android" ]
    then
      break
    fi
    echo "This does not look like a valid Android SDK path."
  done

  echo "Android NDK path:"
  while true
  do
    read ANDROID_NDK_HOME
    if [ ! -x "$ANDROID_HOME/ndk-build" ]
    then
      break
    fi
    echo "This does not look like a valid Android NDK path."
  done

  echo "ARM GCC toolchain path:"
  while true
  do
    read ANDROID_ARM_BUILD_HOME
    if [ ! -x "$ANDROID_HOME/arm-eabi-gcc" ]
    then
      break
    fi
    echo "This does not look like a valid ARM GCC toolchain path."
  done
}
# ---------------------------------------------------------

# leave any already valid paths as they are and use a todo tag instead of actual
# paths for the rest; sets SDK, NDK and ARM GCC homes
# ---------------------------------------------------------
detect_android_env_skip() {
  echo "Skipping Android path detection."
  echo "In order to get the Android paths right, you have to manually edit the"
  echo "generated environment setup script [$OUT_SCRIPT] [$OUT_MAKEFILE]."

  check_paths_in_vars_silent ANDROID_HOME || \
    ANDROID_HOME="$SKIPPED_PATH"
  check_paths_in_vars_silent ANDROID_NDK_HOME || \
    ANDROID_NDK_HOME="$SKIPPED_PATH"
  check_paths_in_vars_silent ANDROID_ARM_BUILD_HOME || \
    ANDROID_ARM_BUILD_HOME="$SKIPPED_PATH"
}
# ---------------------------------------------------------

# detect Android environment, i.e., SDK, NDK and ARM GCC homes as well as a
# number of other Android paths; AVD specific settings are not detected here
# ---------------------------------------------------------
detect_android_env() {
  echo "Detecting Android development environment paths..."

  detect_android_env_quick
  check_paths_in_vars_silent ANDROID_HOME ANDROID_NDK_HOME \
    ANDROID_ARM_BUILD_HOME 

  if [ $? -ne 0 ]
  then
    echo "Quick detect failed."
    echo ""
    echo "Select another detection option!"

    local options
    options=$options' "Search home directory"'
    options=$options' "Search (almost) everywhere"'
    options=$options' "Don'"'"'t search, enter manually"'
    options=$options' "Skip"'

    local SKIP=""
    PS3="What to do? "
    while true
    do
      eval display_list "$options 'Quit'"
      printf "$PS3"
      read REPLY
      case "$REPLY" in
        1 ) echo ""; detect_android_env_search "$HOME"; break;;
        2 ) echo ""; detect_android_env_search "/"; break;;
        3 ) echo ""; detect_android_env_manual; break;;
        4 ) echo ""; detect_android_env_skip; SKIP="1"; break;;
        5 )
          echo "Aborted by user."
          exit 1
          break
          ;;
        * )
          echo "Invalid option. Try again!"
          continue
          ;;
      esac
    done
  fi

  ANDROID_ARM_BUILD_BIN='${ANDROID_ARM_BUILD_HOME}/bin'
  ANDROID_SDK_TOOLS='${ANDROID_HOME}/tools'
  ANDROID_PLATFORM_TOOLS='${ANDROID_HOME}/platform-tools'
  ANDROID_SYSIMG_DIR='${ANDROID_HOME}/system-images'
  ANDROID_AVD_DIR='${HOME}/.android/avd'
  ANDROID_NDK_TOOLCHAINS='${ANDROID_NDK_HOME}/toolchains'
  ANDROID_NDK_PLATFORMS='${ANDROID_NDK_HOME}/platforms'

  check_paths_in_vars ANDROID_HOME ANDROID_NDK_HOME \
    ANDROID_ARM_BUILD_HOME ANDROID_ARM_BUILD_BIN ANDROID_SDK_TOOLS \
    ANDROID_PLATFORM_TOOLS ANDROID_SYSIMG_DIR ANDROID_AVD_DIR \
    ANDROID_NDK_TOOLCHAINS ANDROID_NDK_PLATFORMS
  RET=$?
  if [ -z "$SKIP" ] && [ $RET -ne 0 ]
  then
    echo "ERROR: Encountered $RET invalid Android dev paths!"
    echo "       Cannot continue!"
    exit 1
  fi

  ANDROID_PATHS_TO_EXPORT="${ANDROID_PATHS_TO_EXPORT} ANDROID_HOME"
  ANDROID_PATHS_TO_EXPORT="${ANDROID_PATHS_TO_EXPORT} ANDROID_NDK_HOME"
  ANDROID_PATHS_TO_EXPORT="${ANDROID_PATHS_TO_EXPORT} ANDROID_ARM_BUILD_HOME"
  ANDROID_PATHS_TO_EXPORT="${ANDROID_PATHS_TO_EXPORT} ANDROID_PLATFORM_TOOLS"
  ANDROID_PATHS_TO_EXPORT="${ANDROID_PATHS_TO_EXPORT} ANDROID_SDK_TOOLS"
  ANDROID_PATHS_TO_EXPORT="${ANDROID_PATHS_TO_EXPORT} ANDROID_SYSIMG_DIR"
  ANDROID_PATHS_TO_EXPORT="${ANDROID_PATHS_TO_EXPORT} ANDROID_AVD_DIR"
  ANDROID_PATHS_TO_EXPORT="${ANDROID_PATHS_TO_EXPORT} ANDROID_NDK_TOOLCHAINS"
  ANDROID_PATHS_TO_EXPORT="${ANDROID_PATHS_TO_EXPORT} ANDROID_NDK_PLATFORMS"
  ANDROID_PATHS_TO_ADD="${ANDROID_PATHS_TO_ADD} ANDROID_ARM_BUILD_BIN"

  ADD_TO_PATH="${ADD_TO_PATH} ANDROID_SDK_TOOLS ANDROID_PLATFORM_TOOLS"
  ADD_TO_PATH="${ADD_TO_PATH} ANDROID_ARM_BUILD_BIN ANDROID_NDK_HOME"
}
# ---------------------------------------------------------

# setup env vars for Android kernel compilation
# ---------------------------------------------------------
setup_kern_compile() {
  echo "Android kernel compilation environment..."
  ARCH="arm"
  SUBARCH="arm"
  CROSS_COMPILE="arm-eabi-"

  KERN_COMPILE_EXPVARS="${KERN_COMPILE_EXPVARS} ARCH SUBARCH CROSS_COMPILE"
}
# ---------------------------------------------------------

# detect general stuff like local architecture
# ---------------------------------------------------------
detect_general() {
  echo "Detecting general settings and configuration..."

  LARCH="$(arch)"
  if [ -z "$LARCH" ]
  then
    echo "WARNING: Could not detect local architecture."
    echo "         Assuming x86_64."
    LARCH="x86_64"
  fi

  echo "Detected settings:"
  var_assign LARCH

  GENERAL_SETTINGS_TO_EXPORT="${GENERAL_SETTINGS_TO_EXPORT} LARCH"

  setup_kern_compile
}
# ---------------------------------------------------------

# find homes of the ANDROID FI repo and its two submodules; uses the location of
# this script as basis
# ---------------------------------------------------------
detect_repo_home_paths() {
  echo "Detecting Android FI repository home paths..."
  
  # find own dir, i.e. this script file's dir (not working dir)
  local AFI_CONFIG_DIR="$(to_abs_dir "$(save_dirname "$THIS_SCRIPT")")"
  [ ! -d "$AFI_CONFIG_DIR" ] && \
    echo "ERROR: Cannot find generator script location!" && \
    exit 1

  # find all repo home dirs
  AFI_HOME="$(to_abs_dir "$AFI_CONFIG_DIR/..")"
  AFI_GRINDER_HOME='${AFI_HOME}/GRINDER'
  AFI_KERNEL_HOME='${AFI_HOME}/goldfish_kernel'

  check_paths_in_vars AFI_HOME AFI_GRINDER_HOME AFI_KERNEL_HOME
  RET=$? && [ $RET != 0 ] && \
    echo "ERROR: Encountered $RET invalid home paths!" && \
    echo "       Cannot continue!" && \
    exit 1

  HOME_PATHS_TO_EXPORT="${HOME_PATHS_TO_EXPORT} AFI_HOME AFI_GRINDER_HOME"
  HOME_PATHS_TO_EXPORT="${HOME_PATHS_TO_EXPORT} AFI_KERNEL_HOME"
}
# ---------------------------------------------------------

# gather other relevant Android FI paths; uses the repo home locations as basis
# ---------------------------------------------------------
detect_android_fi_dirs() {
  echo "Detecting misc Android FI directory paths..."
  # find all other dirs
  AFI_CANDE_DIR='${AFI_HOME}/cande'
  AFI_CANDE_LIGHT_DIR='${AFI_CANDE_DIR}/cande_light_detector'
  AFI_CANDE_HEAVY_DIR='${AFI_CANDE_DIR}/cande_heavy_detector'
  AFI_CONFIG_DIR='${AFI_HOME}/config'
  AFI_DBSCRIPT_DIR='${AFI_HOME}/db-scripts'
  AFI_FISCRIPT_DIR='${AFI_HOME}/fault_injection_scripts'
  AFI_SCRIPT_DIR='${AFI_HOME}/scripts'
  AFI_SAR_DIR='${AFI_HOME}/sar-android'
  AFI_GETDELAYS_DIR='${AFI_HOME}/getdelays'
  AFI_GRINDER_LKM_DIR='${AFI_HOME}/grinder-lkm'
  AFI_GRINDER_ANDROID_DIR='${AFI_HOME}/grinder-android'
  AFI_WORKLOAD_DIR='${AFI_HOME}/workload'
  AFI_IMAGES_DIR='${AFI_HOME}/images'
  AFI_TMP_IMAGES_DIR='/tmp/afi'

  check_paths_in_vars AFI_CONFIG_DIR AFI_CANDE_DIR AFI_CANDE_LIGHT_DIR \
    AFI_CANDE_HEAVY_DIR AFI_DBSCRIPT_DIR \
    AFI_FISCRIPT_DIR AFI_GRINDER_LKM_DIR AFI_GRINDER_ANDROID_DIR \
    AFI_WORKLOAD_DIR AFI_SCRIPT_DIR AFI_SAR_DIR \
    AFI_GETDELAYS_DIR AFI_IMAGES_DIR
  RET=$? && [ $RET != 0 ] && \
    echo "ERROR: Encountered $RET invalid Android FI paths!" && \
    echo "       Generated script must be completed manually."

  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_CANDE_DIR AFI_CANDE_LIGHT_DIR"
  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_CANDE_HEAVY_DIR"
  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_DBSCRIPT_DIR AFI_FISCRIPT_DIR"
  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_SCRIPT_DIR AFI_SAR_DIR"
  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_IMAGES_DIR AFI_GETDELAYS_DIR"
  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_TMP_IMAGES_DIR"
  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_GRINDER_LKM_DIR"
  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_GRINDER_ANDROID_DIR"
  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_WORKLOAD_DIR"

  # some self-built tools, not checked for existence since probably not compiled
  # yet
  AFI_EXE_GETDELAYS_NAME="getdelays"
  AFI_EXE_GETDELAYS='${AFI_GETDELAYS_DIR}/${AFI_EXE_GETDELAYS_NAME}'

  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_EXE_GETDELAYS_NAME"
  AFI_PATHS_TO_EXPORT="${AFI_PATHS_TO_EXPORT} AFI_EXE_GETDELAYS"
}
# ---------------------------------------------------------


# detect AVD settings that are used for the experiments
# ---------------------------------------------------------
detect_avd_settings() {
  echo "Detecting AVD settings for Android FI experiments..."

  local TMP="$(ev "$ANDROID_AVD_DIR")"
  [ -z "$TMP" ] && \
    echo "ERROR: Cannot find AVDs." && \
    echo "       Use the Android SDK tools to create an AVD." && \
    exit 1

  local AVDS
  AVDS=$(cd "$TMP" && find *.avd -maxdepth 0 -type d|sed "s/^\(.*\).avd$/'\1'/")
  eval select_entry "''" ${AVDS}
  echo ""
  AFI_AVD_NAME="$REPLY"
  AFI_AVD_DIR='${ANDROID_AVD_DIR}/${AFI_AVD_NAME}.avd'

  [ ! -d "$(ev2 "$AFI_AVD_DIR")" ] && \
    echo "ERROR: Invalid AVD directory!" && \
    echo "       Cannot continue." && \
    exit 1

  AFI_AVD_API="$(grep -o -E "target=android-[^[:blank:]]+" \
    "$(ev "$ANDROID_AVD_DIR/${AFI_AVD_NAME}.ini")")"
  AFI_AVD_API="${AFI_AVD_API#target=android-}"

  # note: double 'ev' needed due to double nesting of vars in path
  AFI_AVD_ABI="$(grep -o -E "abi.type=[^[:blank:]]+" \
    "$(ev2 "$AFI_AVD_DIR/config.ini")")"
  AFI_AVD_ABI="${AFI_AVD_ABI#abi.type=}"

  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_AVD_NAME AFI_AVD_DIR"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_AVD_API AFI_AVD_ABI"
}
# ---------------------------------------------------------

setup_ndk_toolchain() {
  echo "NDK toolchain setup..."

  case "$ARCH" in
    "arm" )
      AFI_NDK_HOST="arm-linux-androideabi"
      AFI_NDK_TCPREFIX="$AFI_NDK_HOST"
      ;;
    "x86" )
      AFI_NDK_HOST="i686-linux-android"
      AFI_NDK_TCPREFIX="x86"
      ;;
    "mips" )
      AFI_NDK_HOST="mipsel-linux-android"
      AFI_NDK_TCPREFIX="$AFI_NDK_HOST"
      ;;
    * )
      echo "ERROR: Unknown architecture Android '${ARCH}'."
      echo "       Cannot select NDK host."
      echo "       Cannot continue."
      exit 1;;
  esac

  local TOOLCHAINS
  echo $AFI_NDK_TOOLCHAINS $AFI_NDK_HOST
  TOOLCHAINS=$(cd "$ANDROID_NDK_HOME/toolchains" &&
               find ${AFI_NDK_HOST}-* -maxdepth 0 -type d)
  eval select_entry "'Multiple possible toolchains.\n  Note: This project is \
not setup for toolchians other than gcc.'" ${TOOLCHAINS}

  AFI_NDK_TCNAME="$REPLY"
  AFI_NDK_GCC_VER="$(echo $AFI_NDK_TCNAME|sed 's/.*-\(.*\)/\1/')"
  AFI_NDK_TOOLCHAIN='${ANDROID_NDK_TOOLCHAINS}/${AFI_NDK_TCNAME}'
  AFI_NDK_TOOLCHAIN="$AFI_NDK_TOOLCHAIN"'/prebuilt/linux-${LARCH}'
  AFI_NDK_SYSROOT='${ANDROID_NDK_PLATFORMS}/android-${AFI_AVD_API}/arch-${ARCH}'

  check_paths_in_vars AFI_NDK_TOOLCHAIN AFI_NDK_SYSROOT
  RET=$? && [ $RET != 0 ] && \
    echo "ERROR: Encountered $RET invalid Android FI paths!" && \
    echo "       Generated script must be edited manually."

  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_NDK_GCC_VER AFI_NDK_HOST"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_NDK_TCNAME"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_NDK_TOOLCHAIN AFI_NDK_SYSROOT"
}

# experiment settings
# ---------------------------------------------------------
setup_experiment() {
  echo "Experiment setup..."

  # TODO: ask for at least some of this stuff

  # images paths (on host)
  AFI_KERNEL_BIMAGE='${AFI_KERNEL_HOME}/arch/arm/boot/zImage'
  AFI_SYSIMG='${AFI_TMP_IMAGES_DIR}/system-afi.img'
  AFI_SDIMG='${AFI_TMP_IMAGES_DIR}/sdcard.img'
  AFI_VSDIMG='${AFI_IMAGES_DIR}/vanilla_sdcard.img'
  AFI_DATIMG='${AFI_TMP_IMAGES_DIR}/userdata-afi.img'
  AFI_VDATIMG='${AFI_IMAGES_DIR}/vanilla_userdata-afi.img'

  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_KERNEL_BIMAGE AFI_SYSIMG"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_SDIMG AFI_VSDIMG"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_DATIMG AFI_VDATIMG"

  # AFI names and paths (on host)
  AFI_MOD_NAME="goldfish"
  AFI_MOD_DIR='${AFI_KERNEL_HOME}/drivers/mmc/host'
  AFI_MOD_PATH='${AFI_MOD_DIR}/${AFI_MOD_NAME}.ko'
  AFI_GRINDER_LKM_NAME='grinder'
  AFI_GRINDER_LKM_PATH='${AFI_GRINDER_LKM_DIR}/${AFI_GRINDER_LKM_NAME}.ko'
  AFI_CANDE_LIGHT_NAME='cande_light_detector'
  AFI_CANDE_HEAVY_NAME='cande_heavy_detector'
  AFI_CANDE_LIGHT_PATH='${AFI_CANDE_LIGHT_DIR}/${AFI_CANDE_LIGHT_NAME}'
  AFI_CANDE_HEAVY_PATH='${AFI_CANDE_HEAVY_DIR}/${AFI_CANDE_HEAVY_NAME}.ko'

  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_MOD_NAME AFI_MOD_DIR"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_MOD_PATH AFI_GRINDER_LKM_NAME"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_GRINDER_LKM_PATH"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_CANDE_LIGHT_NAME"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_CANDE_HEAVY_NAME"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_CANDE_LIGHT_PATH"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_CANDE_HEAVY_PATH"

  # AVD system paths (on target)
  AFI_AVDSYS_HOME="/data/afi"
  AFI_AVDSYS_BIN_DIR='${AFI_AVDSYS_HOME}/bin'
  AFI_AVDSYS_LIB_DIR='${AFI_AVDSYS_HOME}/lib'
  AFI_AVDSYS_MOD_DIR='${AFI_AVDSYS_HOME}/modules'

  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_AVDSYS_HOME AFI_AVDSYS_BIN_DIR"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_AVDSYS_LIB_DIR"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_AVDSYS_MOD_DIR"

  # workload settings
  AFI_WL_NAME="workload-debug"
  AFI_WL_PACK="de.grinder.android_fi"
  AFI_WL_CLASS="de.grinder.android_fi.Workload"
  AFI_WL_PATH='${AFI_WORKLOAD_DIR}/bin/${AFI_WL_NAME}.apk'
  AFI_WL_FLAGFILE='/data/data/${AFI_WL_PACK}/files/workload_state'

  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_WL_NAME AFI_WL_PACK"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_WL_CLASS AFI_WL_PATH"
  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_WL_FLAGFILE"

  detect_avd_settings

  AFI_VSYSIMG_BASE='${ANDROID_SYSIMG_DIR}/android-${AFI_AVD_API}'
  AFI_VSYSIMG="$AFI_VSYSIMG_BASE"'/default/${AFI_AVD_ABI}/system.img'
  # on some systems the SDK image paths differ slightly,
  # so if the first path does not exist, try the alternative one
  check_files_in_vars AFI_VSYSIMG
  if [ $RET != 0 ]
  then
    echo "Trying alternative path..."
    AFI_VSYSIMG="$AFI_VSYSIMG_BASE"'/${AFI_AVD_ABI}/system.img'
    check_paths_in_vars AFI_VSYSIMG
    RET=$? && [ $RET != 0 ] && \
      echo "ERROR: Encountered invalid vanilla system image path!" && \
      echo "       Generated script must be edited manually."
  fi

  EXPERIMENT_EXPVARS="${EXPERIMENT_EXPVARS} AFI_VSYSIMG"

  setup_ndk_toolchain

  echo "Experiment settings:"
  var_assign $EXPERIMENT_EXPVARS
}
# ---------------------------------------------------------

# script main
# ---------------------------------------------------------
main() {
  echo ""
  echo "Android FI environment setup script generator."
  echo ""

  # prevent accidental overwriting of old script versions
  if [ -e "$OUT_SCRIPT" ]
  then
    echo "WARNING: Old environment script [$OUT_SCRIPT] detected."
    echo "         Continuing will overwrite the old script."
    printf "Do you want to continue? (y/N) "
    read REPLY
    [ "$REPLY" != "y" ] && echo "Aborted by user." && exit 1
    echo ""
  fi

  # do the detection step by step and then write to script file
  detect_general
  echo ""
  detect_repo_home_paths
  echo ""
  detect_android_fi_dirs
  echo ""
  detect_android_env
  echo ""
  setup_experiment
  echo ""
  write_script
  echo ""

  echo "Fin."
}
# ---------------------------------------------------------

var_assign() {
  for NAM in $@
  do
    eval CONT=\$$NAM
    echo "${NAM}=\"$CONT\""
  done
}

var_assign_export() {
  for NAM in $@
  do
    eval CONT=\$$NAM
    echo "export ${NAM}=\"$CONT\""
  done
}

to_path_ext() {
  local LST="$*"
  local OUT=""
  for PA in $LST
  do
    OUT="${OUT}\${$PA}:"
  done
  echo "$OUT"
}



write_script() {
  echo "Writing environment script to [$OUT_SCRIPT]..."
  set -e

  VARS="
# General settings and configuration
$(var_assign_export "$GENERAL_SETTINGS_TO_EXPORT")

# Android kernel build configuration
$(var_assign_export "$KERN_COMPILE_EXPVARS")

# Android development environment
$(var_assign_export "$ANDROID_PATHS_TO_EXPORT")

# additional Android paths not for export
$(var_assign "$ANDROID_PATHS_TO_ADD")

# Android FI git repo home locations
$(var_assign_export "$HOME_PATHS_TO_EXPORT")

# various Android FI locations
$(var_assign_export "$AFI_PATHS_TO_EXPORT")

# experiment settings
$(var_assign_export "$EXPERIMENT_EXPVARS")

# PATH extension
export PATH="$(to_path_ext "$ADD_TO_PATH")\${PATH}"
"

  { cat <<SCRIPT-OUTPUT-DELIM
#!/bin/sh
#
# Android FI environment setup script.
# Source this script in your shell in order to setup your environment for
# working with and building the Android FI project.
#
# Automatically generated by $THIS_SCRIPT
# Date of creation: $(date)
#
# You may have to manually edit this file. However, beware that all manual
# edits are overwritten if you re-execute the generator script.
# Be sure to also edit $OUT_MAKEFILE.
#

# do not run multiple times
if [ -n "\$AFI_HOME" ]; then
  echo "Your environment seems to be already set up for Android FI."
  echo "No need to re-run environment setup script."
  return 0
fi
SCRIPT-OUTPUT-DELIM
  } > "$OUT_SCRIPT"

  echo "$VARS" >> "$OUT_SCRIPT"

  chmod a+x "$OUT_SCRIPT"

  echo "Creating a Makefile-compatible version..."

  { cat <<SCRIPT-OUTPUT-DELIM
# Android FI environment setup Makefile.
#
# Automatically generated by $THIS_SCRIPT
# Date of creation: $(date)
#
# You may have to manually edit this file. However, beware that all manual
# edits are overwritten if you re-execute the generator script.
# Be sure to also edit $OUT_SCRIPT.

HOME="$HOME"
SCRIPT-OUTPUT-DELIM
  } > "$OUT_MAKEFILE"

  echo "$VARS" >> "$OUT_MAKEFILE"

  # Use sed to adapt the syntax
  sed -i -e 's/=/:=/g' \
         -e 's/"//g'   \
      "$OUT_MAKEFILE"

  set +e

  local UNSET_PATHS="$(grep -e "$INVALID_PATH" -e "$SKIPPED_PATH" "$OUT_SCRIPT")"
  if [ -n "UNSET_PATHS" ]
  then
    echo "Script run complete, exiting with an error due to unset paths:"
    echo "$UNSET_PATHS"

    echo ""
    echo "Please fix manually."
    exit 1
  fi
}

# start the magic
main

# vim: expandtab ts=2 sw=2 sts=0 tw=80 cc=80:
