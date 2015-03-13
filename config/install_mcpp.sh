#!/bin/zsh

. ../env.sh

# Please adjust to your environment!
PREBUILT_PATH=$ANDROID_ARM_BUILD_HOME
MCPP_DIR=../mcpp-2.7.2
GCC_VER=$("$ANDROID_ARM_BUILD_HOME"/bin/"$CROSS_COMPILE"gcc -dumpversion)
GCC_SHORTVER=$(echo "$GCC_VER"|sed 's/^\([[:digit:]]*\)\.\([[:digit:]]*\).*$/\1\2/g')

FAKE_DIR=${PREBUILT_PATH}/lib/gcc/arm-eabi/${GCC_VER}/include/mcpp-gcc-arm

cd ../goldfish_kernel
patch -p0 < ../config/kbuild_fixdep.patch
patch -p0 < ../config/kbuild_genksyms.patch

cd $MCPP_DIR
make install CC=arm-eabi-gcc CXX=arm-eabi-g++

mkdir -p $FAKE_DIR
touch ${FAKE_DIR}/gcc${GCC_SHORTVER}_predef_old.h
touch ${FAKE_DIR}/gcc${GCC_SHORTVER}_predef_std.h
touch ${FAKE_DIR}/gxx${GCC_SHORTVER}_predef_old.h
touch ${FAKE_DIR}/gxx${GCC_SHORTVER}_predef_std.h
