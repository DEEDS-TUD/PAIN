#!/bin/bash
#
# Unload the interface injection modules
#

# little helper to script location
THIS_SCRIPT="${BASH_SOURCE[0]}"
THIS_SCRIPT_DIR="$(dirname $THIS_SCRIPT)"

# check env setup
[[ -z "$AFI_HOME" ]] && { \
  . "$THIS_SCRIPT_DIR/../env.sh" || \
  { echo "ERROR: Invalid environment setup."; exit 1; };
}

echo "Unloading modules..."
adb shell rmmod "$AFI_MOD_NAME"
adb shell rmmod "w_${AFI_MOD_NAME}"
adb shell rmmod "$AFI_GRINDER_LKM_NAME"

