#!/bin/bash
#
# Load the interface injection modules
#

# little helper to script location
THIS_SCRIPT="${BASH_SOURCE[0]}"
THIS_SCRIPT_DIR="$(dirname $THIS_SCRIPT)"

# check env setup
[[ -z "$AFI_HOME" ]] && { \
  . "$THIS_SCRIPT_DIR/../env.sh" || \
  { echo "ERROR: Invalid environment setup."; exit 1; };
}

echo "Waiting for device..."
adb wait-for-device
echo "Preparing /system..."
adb remount
#adb shell mkdir -p /system/lib/modules

# push the module under test and its wrapper
echo "Pushing module [$AFI_MOD_NAME] and its wrapper to [$AFI_AVDSYS_MOD_DIR]..."
#adb push "$AFI_GRINDER_LKM_PATH" "$AFI_AVD_MOD_DIR"
adb push "$AFI_WRAPPERGEN_DIR/out/w_${AFI_MOD_NAME}.ko" "$AFI_AVDSYS_MOD_DIR"
adb push "$AFI_WRAPPERGEN_DIR/out/${AFI_MOD_NAME}.ko" "$AFI_AVDSYS_MOD_DIR"

# load modules
echo "Loading GRINDER LKM..."
adb shell insmod "$AFI_AVDSYS_MOD_DIR/${AFI_GRINDER_LKM_NAME}.ko"
echo "Loading wrapper module..."
adb shell insmod "$AFI_AVDSYS_MOD_DIR/w_${AFI_MOD_NAME}.ko"
echo "Loading instrumented module..."
adb shell insmod "$AFI_AVDSYS_MOD_DIR/${AFI_MOD_NAME}.ko"

# Original goldfish
# adb push "$AFI_KERNEL_HOME/drivers/mmc/host/goldfish.ko" "/system/lib/modules"
# adb shell insmod "/system/lib/modules/goldfish.ko"


echo "Loaded modules"
echo "------------------"
adb shell lsmod
echo "------------------"

