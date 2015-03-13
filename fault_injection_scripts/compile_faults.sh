#!/bin/bash

ROOTDIR=/home/fdpfi/campaign/fault_injection_mysql/mysql-5.1.34
BACKUPDIR=/home/fdpfi/campaign/fault_injection_mysql/mysql-5.1.34-backup	#copia di backup
FAULTYDIR=/mnt/samba/mysql_faulty
BINPREFIX=mysqld
COMPILESUBDIR=sql/

#PATCHLIST=/home/fdpfi/campaign/fault_injection_mysql/progs/coverage-mysqlslap.txt
#PATCHLIST=/home/fdpfi/campaign/fault_injection_mysql/progs/coverage-mysqltest-mainsuite.txt.diff
PATCHLIST=/home/fdpfi/campaign/fault_injection_mysql/progs/patches_to_compile.txt


PROGDIR=`cd $(dirname $0); pwd`


function print_dashes {
	echo
	echo
	echo "---------------------------------------------------------------"
	echo
	echo
}


echo "Cleaning temporaries"
TEMP=`pwd`/__TMP_COMPILE_FAULTS
rm -rf $TEMP
mkdir $TEMP


echo "Restoring original src files"
cp $BACKUPDIR/$COMPILESUBDIR/*.cc $ROOTDIR/$COMPILESUBDIR
cp $BACKUPDIR/$COMPILESUBDIR/*.c $ROOTDIR/$COMPILESUBDIR
cp $BACKUPDIR/$COMPILESUBDIR/*.h $ROOTDIR/$COMPILESUBDIR



cd $ROOTDIR

TOT_FAULTS=`wc -l $PATCHLIST|awk '{print $1}'`

for I in `seq 1 ${TOT_FAULTS}`
do
	PATCH=`awk 'NR=='$I' {print $0}' $PATCHLIST`
	FAULT=`basename $PATCH .patch`

	if [ -e $FAULTYDIR/$BINPREFIX_$FAULT ]
	then
		echo "Skipping patch: $PATCH"
		print_dashes
		continue
	fi



	echo "Testing patch: $PATCH"

	patch -f --dry-run -p0 < $PATCH

	if [ $? -ne 0 ]
	then
		echo "Unable to apply patch: $PATCH"
		print_dashes
		continue
	fi


	# Get patched files list
	PATCHED=(`awk '/^---/ {print $2}' $PATCH`)

	# Save patched files
	for FILE in $(seq 0 $((${#PATCHED[@]} - 1)))
	do
		mkdir -p `dirname $TEMP/${PATCHED[$FILE]}`
		cp -R ${PATCHED[$FILE]} $TEMP/${PATCHED[$FILE]}
	done


	echo "Applying patch: $PATCH"

	patch -f -p0 < $PATCH

	if [ $? -ne 0 ]
	then
		echo "Unable to apply patch: $PATCH"
		print_dashes
		continue
	fi


	make -j 2


	if [ $? -ne 0 ]
	then
		echo
		echo
		echo "Compilation failed."
		#echo "Press ENTER to continue."
		#read FOO
	else
		# Successful compilation
		cp $COMPILESUBDIR/$BINPREFIX $FAULTYDIR/$BINPREFIX_$FAULT
	fi


	echo "Reversing patch: $PATCH"

	# Restore patched files
    for FILE in $(seq 0 $((${#PATCHED[@]} - 1)))
    do
        cp -R $TEMP/${PATCHED[$FILE]} ${PATCHED[$FILE]}
    done

#	patch -R -p0 < $PATCH

#	if [ $? -ne 0 ]
#	then
#		echo "Unable to reverse patch: $PATCH"
#		#echo "Press ENTER to continue."
#		#read FOO
#
#		echo "Restoring src files"
#		cp $BACKUPDIR/$COMPILESUBDIR/*.cc $ROOTDIR/$COMPILESUBDIR
#		cp $BACKUPDIR/$COMPILESUBDIR/*.c $ROOTDIR/$COMPILESUBDIR
#		cp $BACKUPDIR/$COMPILESUBDIR/*.h $ROOTDIR/$COMPILESUBDIR
#
#		print_dashes
#		continue
#	fi


	echo
	echo
	echo "---------------------------------------------------------------"
	echo
	echo

done

echo
echo "Compilation ended"
echo

echo "Restoring original src files"
cp $BACKUPDIR/$COMPILESUBDIR/*.cc $ROOTDIR/$COMPILESUBDIR
cp $BACKUPDIR/$COMPILESUBDIR/*.c $ROOTDIR/$COMPILESUBDIR
cp $BACKUPDIR/$COMPILESUBDIR/*.h $ROOTDIR/$COMPILESUBDIR

