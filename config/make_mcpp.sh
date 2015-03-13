#!/bin/zsh

OLD_PATH=$PATH
source ../env.sh
PATH=$OLD_PATH

# please adjust to your environment!
MCPP_PARENT=..
MCPP_VER=2.7.2
PREBUILT_DIR=$ANDROID_ARM_BUILD_HOME
SCRIPT_HOME=$(pwd)
GCC_VER=$("$ANDROID_ARM_BUILD_HOME"/bin/"$CROSS_COMPILE"gcc -dumpversion)
GCC_MIN_VER=$(echo "$GCC_VER"|sed 's/.*\.\([[:digit:]]*\)\..*/\1/;q')

# src/config.h parameters to change
CPLUS_INCLUDE_DIR1=$PREBUILT_DIR/lib/gcc/arm-eabi/${GCC_VER}/include
CPLUS_INCLUDE_DIR2=$PREBUILT_DIR/include
CPLUS_INCLUDE_DIR3=$PREBUILT_DIR/lib/gcc/arm-eabi/${GCC_VER}/install-tools/include
C_INCLUDE_DIR1=$PREBUILT_DIR/lib/gcc/arm-eabi/${GCC_VER}/include
C_INCLUDE_DIR2=$PREBUILT_DIR/include
C_INCLUDE_DIR3=$PREBUILT_DIR/lib/gcc/arm-eabi/${GCC_VER}/install-tools/include
INC_DIR=$PREBUILT_DIR/lib/gcc/arm-eabi/${GCC_VER}/include

# src/Makefile parameters to change
bindir=$PREBUILT_DIR/libexec/gcc/arm-eabi/${GCC_VER}
cpp_call=$PREBUILT_DIR/libexec/gcc/arm-eabi/${GCC_VER}/cc1
gcc_path=$PREBUILT_DIR/bin/arm-eabi-gcc
inc_dir=$PREBUILT_DIR/lib/gcc/arm-eabi/${GCC_VER}/include
includedir=$PREBUILT_DIR/prebuilt/include

#source setup_env.sh

echo "Changing directory from $(pwd) to $MCPP_DIR"
MCPP_SRC=mcpp-$MCPP_VER
MCPP_SRC_BALL=${MCPP_SRC}.tar.gz
mkdir -p $MCPP_PARENT
cd $MCPP_PARENT
if [ ! -f $MCPP_SRC/configure ]; then
	echo "Downloading MCPP source tar-ball"
	wget http://downloads.sourceforge.net/mcpp/$MCPP_SRC_BALL
	echo "Unpacking MCPP source tar-ball"
	tar xaf $MCPP_SRC_BALL
	rm $MCPP_SRC_BALL
fi
cd $MCPP_SRC
echo "Configuring MCPP"
./configure --enable-replace-cpp
echo "Patching src/config.h"
sed -r -i.bak \
-e 's|(^#define\sCPLUS_INCLUDE_DIR1\s).*($)|\1"'"${CPLUS_INCLUDE_DIR1}"'"\2|' \
-e 's|(^#define\sCPLUS_INCLUDE_DIR2\s).*($)|\1"'"${CPLUS_INCLUDE_DIR2}"'"\2|' \
-e 's|(^#define\sCPLUS_INCLUDE_DIR3\s).*($)|\1"'"${CPLUS_INCLUDE_DIR3}"'"\2|' \
-e 's|(^#define\sC_INCLUDE_DIR1\s).*($)|\1"'"${CPLUS_INCLUDE_DIR1}"'"\2|' \
-e 's|(^#define\sC_INCLUDE_DIR2\s).*($)|\1"'"${CPLUS_INCLUDE_DIR2}"'"\2|' \
-e 's|(^#define\sC_INCLUDE_DIR3\s).*($)|\1"'"${CPLUS_INCLUDE_DIR3}"'"\2|' \
-e 's|(^#define\sINC_DIR\s).*($)|\1"'"${INC_DIR}"'"\2|' \
-e 's|(^#define\sGCC_MINOR_VERSION\s).*($)|\1"'"${GCC_MIN_VER}"'"\2|' \
src/config.h
echo "Patching src/Makefile"
sed -r -i.bak \
-e 's|(^bindir\s=\s).*($)|\1'"${bindir}"'\2|' \
-e 's|(^cpp_call\s=\s).*($)|\1'"${cpp_call}"'\2|' \
-e 's|(^gcc_path\s=\s).*($)|\1'"${gcc_path}"'\2|' \
-e 's|(^inc_dir\s=\s).*($)|\1'"${inc_dir}"'\2|' \
-e 's|(^includedir\s=\s).*($)|\1'"${includedir}"'\2|' \
-e 's|(^gcc_min_ver\s=\s).*($)|\1"'"${GCC_MIN_VER}"'"\2|' \
src/Makefile
echo "Invoking make"
make
echo "To install MCPP invoke install_mcpp.sh as superuser."
echo "To uninstall MCPP invoke uninstall_mcpp.sh as superuser."
