#!/bin/zsh

# Please adjust to your environment!
MCPP_DIR=../mcpp-2.7.2

cp -v fixdep.c.orig ../goldfish_kernel/scripts/basic/fixdep.c
cp -v genksyms.c.orig ../goldfish_kernel/scripts/genksyms/genksyms.c

cd $MCPP_DIR
make uninstall CC=arm-eabi-gcc CXX=arm-eabi-g++
