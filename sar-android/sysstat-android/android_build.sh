#!/bin/sh
#
# Build sar and sadc from sysstat fro Android
#

THIS_SCRIPT_DIR="$(dirname $0)"
cd "$THIS_SCRIPT_DIR"

# check env setup
if [ -z "$AFI_HOME" ]; then
  . ../../env.sh
  if [ $? -ne 0 ]; then
    echo "ERROR: Invalid environment setup."
    exit 1
  fi
fi

export PATH="$AFI_NDK_TOOLCHAIN/bin/:$PATH"
export CPPFLAGS="--sysroot=$AFI_NDK_SYSROOT"
export CFLAGS="--sysroot=$AFI_NDK_SYSROOT -fPIE -pie"

./configure --host="$AFI_NDK_HOST" --disable-nls --prefix="$AFI_AVDSYS_HOME"
make sar sadc
