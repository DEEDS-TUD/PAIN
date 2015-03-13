#!/bin/bash

# little helper to script location
THIS_SCRIPT="${BASH_SOURCE[0]}"
THIS_SCRIPT_DIR="$(dirname $THIS_SCRIPT)"

# check env setup
[[ -z "$AFI_HOME" ]] && { \
  . "$THIS_SCRIPT_DIR/../env.sh" || \
  { echo "ERROR: Invalid environment setup."; exit 1; };
}


MVN="mvn"
MVN_TARGET_CLEANBUILD="clean install"
MVN_TARGET_BUILD="install"
MVN_FLAGS="${MVN_FLAGS-"-DskipTests --offline"}"


echo "Building GRINDER Android module..."
cd "$AFI_GRINDER_ANDROID_DIR"
$MVN $MVN_FLAGS $MVN_TARGET_CLEANBUILD
RET=$?
if [[ $RET == 0 ]]; then
  echo "Successfully built GRINDER Android module."
  echo ""
else
  echo "ERROR: Failed to build GRINDER Android module."
  echo "       Stopping."
  exit 1
fi

echo "Building GRINDER..."
cd "$AFI_GRINDER_HOME"
$MVN $MVN_FLAGS $MVN_TARGET_BUILD
RET=$?
if [[ $RET == 0 ]]; then
  echo "Successfully built GRINDER."
  echo ""
else
  echo "ERROR: Failed to build GRINDER."
  echo "       Stopping."
  exit 1
fi

echo "Fin."

