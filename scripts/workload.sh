#!/bin/bash
#
# Install and execute the workload
#

# little helper to script location
THIS_SCRIPT="${BASH_SOURCE[0]}"
THIS_SCRIPT_DIR="$(dirname $THIS_SCRIPT)"

# check env setup
[[ -z "$AFI_HOME" ]] && { \
  . "$THIS_SCRIPT_DIR/../env.sh" || \
  { echo "ERROR: Invalid environment setup."; exit 1; };
}

echo "Installing workload..."
adb install -r -t "$AFI_WORKLOAD_DIR/bin/${AFI_WL_NAME}.apk"
sleep 1s
echo "Starting workload..."
adb shell am start -n "$AFI_WL_PACK/$AFI_WL_CLASS"
sleep 2s
echo "Un-installing workload..."
adb uninstall "$AFI_WL_PACK"

