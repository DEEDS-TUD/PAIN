#!/bin/sh

if [ -z "$AFI_HOME" ]; then
	. ../env.sh || {
		echo "Could not parse environment. Make sure you've generated one."
		return 1;
	}
fi

make -f Makefile.mutant obj-m="$(find "$1" -name '*.c'|sed 's/c$/o/g'|tr '\n' ' ')"
