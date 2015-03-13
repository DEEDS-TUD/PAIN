#!/bin/bash

ROOTDIR=/home/fdpfi/campaign/fault_injection_mysql/mysql-5.1.34
PATCHLIST= /home/fdpfi/campaign/fault_injection_mysql/progs

PROGDIR=`cd $(dirname $0); pwd`

cd $ROOTDIR

#./configure --prefix=/opt/mysql --with-plugins=all --with-ndb-test
#make CFLAGS="-Wp,-K,-W0 -save-temps" -j 2


find . -path "./sql/*.ii" | while read FILE
do
	echo "Analyzing $FILE"
	$PROGDIR/injection -fs-verbose $FILE
	echo "Fault locations found: "`find . -name $(basename ${FILE})"*patch" | wc -l`
done
