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

emulator64-arm \
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
  -screen "no-touch"

