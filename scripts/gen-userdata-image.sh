#!/bin/bash
#
# Generator script for producing a ready-to-use Android userdata image.
# The generated image is based on the default userdata image, which is selected based
# on the current API level and ABI.
# Generated userdata image is written to path AFI_DATIMG.
#
# REQUIRES
#   - correct env setup (env.sh)
#   - already built binaries and modules
#     - grinder LKM
#     - CANDE heavy detector LKM
#     - CANDE light detector executable
#     - sysstat for android, i.e., sar executable and sadc library
#

# little helper to script location
THIS_SCRIPT="${BASH_SOURCE[0]}"
THIS_SCRIPT_DIR="$(dirname $THIS_SCRIPT)"

# check env setup
[[ -z "$AFI_HOME" ]] && { \
  . "$THIS_SCRIPT_DIR/../env.sh" || \
    { echo "ERROR: Invalid environment setup."; exit 1; };
}

pgrep -x emulator64-arm > /dev/null
if [[ $? -eq 0 ]]; then
  echo An emulator instance is already running, bailing out.
  exit 1
fi

# env paths missing in env script
AFI_CANDE_LIGHT_DIR="${AFI_CANDE_LIGHT_DIR-"$AFI_CANDE_DIR/cande_light_detector"}"
AFI_CANDE_HEAVY_DIR="${AFI_CANDE_HEAVY_DIR-"$AFI_CANDE_DIR/cande_heavy_detector"}"
AFI_CANDE_LIGHT_NAME="${AFI_CANDE_LIGHT_NAME-"cande_light_detector"}"
AFI_CANDE_HEAVY_NAME="${AFI_CANDE_HEAVY_NAME-"cande_heavy_detector"}"

# source userdata image from android SDKs
SRC_IMG="$ANDROID_SYSIMG_DIR/android-${AFI_AVD_API}/default/$AFI_AVD_ABI/userdata.img"
# on some systems the SDK image paths differ slightly, so if the first path does not
# exist, try the alternative one
if [[ ! -f "$SRC_IMG" ]]
then
  SRC_IMG="$ANDROID_SYSIMG_DIR/android-${AFI_AVD_API}/$AFI_AVD_ABI/userdata.img"
fi

AVDSYS_HOME="$AFI_AVDSYS_HOME"
AVDSYS_BIN="$AFI_AVDSYS_BIN_DIR"
AVDSYS_LIB="$AFI_AVDSYS_LIB_DIR"
AVDSYS_MOD="$AFI_AVDSYS_MOD_DIR"

CREATE_DIRS=("$AVDSYS_HOME" "$AVDSYS_BIN" "$AVDSYS_LIB" "$AVDSYS_MOD")
CREATE_DIRS+=("$AVDSYS_LIB/sa")

# files to copy to target image
# pair of arrays, one for source, other for destination; destination must contain full
# path including file name; two entries belong together if they have the same array index
COPY_FILES_SRC+=("$AFI_GRINDER_LKM_DIR/${AFI_GRINDER_LKM_NAME}.ko")
COPY_FILES_DST+=("$AVDSYS_MOD/${AFI_GRINDER_LKM_NAME}.ko")
COPY_FILES_SRC+=("$AFI_MOD_PATH")
COPY_FILES_DST+=("$AVDSYS_MOD/${AFI_MOD_NAME}_orig.ko")
COPY_FILES_SRC+=("$AFI_CANDE_HEAVY_DIR/${AFI_CANDE_HEAVY_NAME}.ko")
COPY_FILES_DST+=("$AVDSYS_MOD/${AFI_CANDE_HEAVY_NAME}.ko")
COPY_FILES_SRC+=("$AFI_CANDE_LIGHT_DIR/$AFI_CANDE_LIGHT_NAME")
COPY_FILES_DST+=("$AVDSYS_BIN/$AFI_CANDE_LIGHT_NAME")

COPY_FILES_SRC+=("$AFI_SAR_DIR/sysstat-android/sar")
COPY_FILES_DST+=("$AVDSYS_BIN/sar")
COPY_FILES_SRC+=("$AFI_SAR_DIR/sysstat-android/sadc")
COPY_FILES_DST+=("$AVDSYS_LIB/sa/sadc")
#COPY_FILES_SRC+=()
#COPY_FILES_DST+=()

ensure_file() {
  [[ ! -f "$*" ]] && \
    echo "ERROR: File [$*] does not exist." && \
    exit 1
}

# $1: logcat buffer
# $2: logcat filter
# $3: search string
wait_for_logcat() {
  bash -c 'echo $$; exec adb logcat -s -b "$1" "$2"' -- "$1" "$2" | {
    read lcpid;
    grep -m 1 "$3";
    kill $lcpid; } &> /dev/null
}

# $1: logcat filter
wait_for_logcat_event() {
  wait_for_logcat 'events' "$1" ''
}

# $1: file
# $2: search string
wait_for_file() {
  bash -c 'echo $$; exec tail -f "$1"' -- "$1" | {
    read tlpid;
    grep -m 1 "$2";
    kill $tlpid; } &> /dev/null
}

mkdir -p "$AFI_TMP_IMAGES_DIR"

echo ""
echo "Android FI userdata image generator"
echo ""
echo "API: $AFI_AVD_API"
echo "ABI: $AFI_AVD_ABI"
echo "Source image: $SRC_IMG"
echo "Target image: $AFI_DATIMG"
echo "Vanilla image: $AFI_VDATIMG"
echo ""

ensure_file "$SRC_IMG"
if [[ -f "$AFI_VDATIMG" ]]
then
  echo "WARNING: Existing vanilla/target image will be overwritten."
  read -p "Do you want to continue? (y/N) "
  [[ "$REPLY" != "y" ]] && echo "Aborted by user." && exit 0
  echo ""
fi

echo "Copying source image..."
cp "$SRC_IMG" "$AFI_DATIMG"
ensure_file "$AFI_DATIMG"

echo "Starting emulator..."
EMUOUT="/tmp/afi-ud-gen.txt"
emulator64-arm \
  -avd ${AFI_AVD_NAME} \
  -data "$AFI_DATIMG" \
  -no-snapshot-save \
  -no-window \
  -verbose \
  -show-kernel \
  -no-boot-anim \
  -no-audio \
  -screen "no-touch" &> "$EMUOUT" &
EMUPID=$!

echo "Waiting for emulator boot-up (will take some time)..."
adb wait-for-device

echo "Emulated Android ready."

# create needed directories
echo "Creating [${#CREATE_DIRS[@]}] directories..."
for DIR in "${CREATE_DIRS[@]}"
do
  echo "DIR: $DIR"
  adb shell mkdir -p "$DIR"
done

# copy needed files
echo "Copying [${#COPY_FILES_SRC[@]}] files..."
CNT=$(( ${#COPY_FILES_SRC[@]} - 1 ))
for i in $(seq 0 $CNT)
do
  echo "FIL: ${COPY_FILES_DST[$i]}"
  adb push "${COPY_FILES_SRC[$i]}" "${COPY_FILES_DST[$i]}"
done

echo "Searching mutants in [$AFI_FISCRIPT_DIR]..."
MUTANT_PATHS=( $(find "$AFI_FISCRIPT_DIR" -name '*.ko') )
echo "Found ${#MUTANT_PATHS[@]} mutants"
echo "Copying mutants..."
for M in "${MUTANT_PATHS[@]}"
do
  echo "MUT: $(basename $M)"
  adb push "$M" "$AVDSYS_MOD"
done

# workload install including waiting phase
echo "Installing workload [$AFI_WL_PATH]..."
echo "Waiting for package manager (will take some time)..."
wait_for_logcat_event 'boot_progress_pms_ready:I'
echo "Package manager available."
echo "Installing workload (will take some time)..."
adb install -r "$AFI_WL_PATH"

echo "Waiting for activity manager..."
wait_for_logcat_event 'boot_progress_ams_ready:I'
echo "Starting workload to allow autostart at next boot..."
adb shell 'am start -W -n de.grinder.android_fi/.Workload'

echo "Waiting for workload end..."
echo "   This will again take an awfully long time. If you haven't already"
echo "   done so, go and grab a cup of black, hot and delicious coffee!"

wait_for_logcat 'main' 'Workload:D' 'workload_finished'
echo "Removing workload state file..."
adb shell rm -f "/data/data/de.grinder.android_fi/files/workload_state"

# AVD shutdown including waiting phase
echo ""
echo "Shutting down Android (again, will take some time)..."
adb shell 'am start -a android.intent.action.ACTION_REQUEST_SHUTDOWN'
sleep 5s
echo "Waiting for shutdown to complete"
wait_for_file "$EMUOUT" "System halted."

# some housekeeping
echo "Some more waiting..."
sleep 10s
echo "Stopping emulator..."
kill $EMUPID
wait $EMUPID
rm -f "$EMUOUT"

# run fsck on image since the shutdown ins not 100% clean
echo "Fixing FS ..."
echo "-----"
fsck.ext4 -f -y "$AFI_DATIMG"
echo "-----"
echo ""

# copy created image for use as vanilla image
echo "Creating vanilla image..."
cp "$AFI_DATIMG" "$AFI_VDATIMG"
ensure_file "$AFI_VDATIMG"

echo ""
echo "Fin."

