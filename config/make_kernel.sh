#!/bin/zsh

#source ../env.sh
cd ../goldfish_kernel
cp -v ../config/config-3.4 .config
make EXTRA_CFLAGS=-save-temps
