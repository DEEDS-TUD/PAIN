#!/bin/bash
#
# Start the Android emulator with AFI system iamge and kernel.
#


# little helper to script location
THIS_SCRIPT="${BASH_SOURCE[0]}"
THIS_SCRIPT_DIR="$(dirname $THIS_SCRIPT)"

# check env setup
[[ -z "$AFI_HOME" ]] && { \
  . "$THIS_SCRIPT_DIR/../env.sh" || \
  { echo "ERROR: Invalid environment setup."; exit 1; };
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

EMUOUT="/tmp/afi-profiling-run.txt"

echo "starting emulator"
../getdelays/getdelays -bdeiqc emulator64-arm \
  -avd ${AFI_AVD_NAME} \
  -kernel ${AFI_KERNEL_BIMAGE} \
  -data "$AFI_DATIMG" \
  -initdata "$AFI_VDATIMG" \
  -sdcard "$AFI_SDIMG" \
  -no-snapshot-save \
  -verbose \
  -show-kernel \
  -no-window \
  -no-boot-anim \
  -no-audio \
  -wipe-data \
  -screen "no-touch" &> "$EMUOUT" &

echo "Waiting for emulator boot-up..."
adb wait-for-device
echo "Emulated Android ready."

echo "Loading MMC card driver..."
adb push "${AFI_MOD_DIR}/${AFI_MOD_NAME}.ko" "$AFI_AVDSYS_MOD_DIR"
adb shell insmod "${AFI_AVDSYS_MOD_DIR}/${AFI_MOD_NAME}.ko"

echo "Waiting for package manager (may take some time)..."
wait_for_logcat_event 'boot_progress_pms_ready:I'
echo "Package manager available."
echo "Installing workload..."
adb install -r -t "$AFI_WORKLOAD_DIR/bin/${AFI_WL_NAME}.apk"

echo "Waiting for activity manager..."
wait_for_logcat_event 'boot_progress_ams_ready:I'
echo "Starting workload..."
adb shell am start -n "$AFI_WL_PACK/$AFI_WL_CLASS"

echo "Waiting for workload execution to complete"
wait_for_logcat 'main' 'Workload:D' 'workload_finished'
echo "Un-installing workload..."
adb uninstall "$AFI_WL_PACK"

echo "Un-loading MMC card driver ... "
adb shell rmmod "${AFI_MOD_NAME}"

# AVD shutdown including waiting phase
echo "Shutting down Android..."
adb shell 'am start -a android.intent.action.ACTION_REQUEST_SHUTDOWN'
sleep 5s
echo "Waiting for shutdown to complete"
wait_for_file "$EMUOUT" "System halted."
# some housekeeping
echo "Some more waiting..."
sleep 10s
echo "Stopping emulator..."
kill $(pidof emulator64-arm)
wait $EMUPID
rm -f "$EMUOUT"
