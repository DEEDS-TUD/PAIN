#!/bin/zsh

PREBUILT_PATH=/hdd/projects/sm-fi/prebuilt/linux-x86/toolchain/arm-eabi-4.4.3/lib/gcc/arm-eabi/4.4.3/include
FAKE_DIR=${PREBUILT_PATH}/mcpp-gcc-arm

mkdir $FAKE_DIR
touch ${FAKE_DIR}/gcc44_predef_old.h
touch ${FAKE_DIR}/gcc44_predef_std.h
touch ${FAKE_DIR}/gxx44_predef_old.h
touch ${FAKE_DIR}/gxx44_predef_std.h