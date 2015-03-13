#!/bin/bash

BINDIR=/opt/mysql/libexec
LOGDIR=/opt/mysql/mysql-test-tmpfs/var/log
#ROOTDIR=/home/fdpfi/campaign/fault_injection_mysql/mysql-5.1.34
FAULTYDIR=/mnt/samba/mysql_faulty/
SAVEDIR=/home/fdpfi/campaign/fault_injection_mysql/save/
MOUNT=/home
EXECBACKUPDIR=/home/fdpfi/campaign/fault_injection_mysql/progs/
EXECNAME=mysqld
TESTLIST=/home/fdpfi/campaign/fault_injection_mysql/progs/faults
TIMEOUT=40	# seconds
TEMPDIR=/opt/mysql/mysql-test-tmpfs/var/tmp


PROGDIR=`cd $(dirname $0); pwd`

cd $PROGDIR




handle_timeout() {

	echo
	echo "----------- Test Timeout (Forced exit) ------------"
	echo

	ps aux|grep "/opt/mysql/sql/mysqld"|grep -v "safe"|grep -v grep|awk '{print $2}'|xargs kill -SIGSEGV > /dev/null

	if [ $? -ne 0 ]
	then
		echo "Kill failed"
	fi

}



echo "Start campaign: "`date`


# Backup del webserver
#if [ ! -e $EXECBACKUP/$EXECNAME ]
#then
#	cp $BINDIR/$EXECNAME $EXECBACKUP/$EXECNAME
#fi


# Abilitazione dei core dump
#echo "Turning on core dumps"
#sudo bash -c "echo `pwd`'/core' | sudo cat > /proc/sys/kernel/core_pattern"
#ulimit -c unlimited


if [ ! -e $TESTLIST ]
then
	echo "Test list missing"
	exit 1
fi

# User-Generated list (faults are filtered by workload coverage)
#find $ROOTDIR -path "*/"$TARGETDIR"/*.patch" | sort > $TESTLIST

TOT_TESTS=`wc -l $TESTLIST|awk '{print $1}'`

for TEST in `seq 1 $TOT_TESTS`
do

	PATCH=`awk 'NR=='$TEST' {print $0}' $TESTLIST`

	# Backdoor for pausing tests
	if [ -e freeze.txt ]
	then
		sleep 1000000000
	fi

	# Stop if there is no available space
	FREE_SPACE_KB=`df|grep $MOUNT|awk '{print $4}'`
	if [ $FREE_SPACE_KB -lt 10000 ]
	then
		echo "Low free disk space on $MOUNT !"
		sleep 1000000000
	fi



	echo
	echo "---------------------------------------------------------------"
	echo



	FAULT=`basename $PATCH .patch`

	echo "Running test: $FAULT"
	date

	#continue

	if [ ! -e $FAULTYDIR/$FAULT ]
	then
		echo "Skipping fault: $FAULT"
		continue
	fi

	if [ -e $SAVEDIR/$FAULT ]
	then
		echo "Test already done: $FAULT"
		continue
	fi



	echo "Clearing logs"
	rm -rf $LOGDIR



	echo "Cleaning testbed"
	# Cleanup
	#
	# 1. Uccidere tutti i processi
	# 2. Rimuovere tutte le risorse UNIX condivise
	# 3. Rimuovere eventuali file temporanei creati per il test
	##ps aux|grep "my"|grep -v grep|awk '{print $2}'|xargs kill -9
	##sleep 2
	ipcclean
	rm -rf $TEMPDIR


	echo "Installing Faulty Server"

	cp $FAULTYDIR/$FAULT $BINDIR/$EXECNAME

	if [ $? -ne 0 ]
	then
		echo "Unable to install faulty executable"
		sleep 1000000000
	fi



	echo "Starting Test"

	./workload-mysql.sh 2>&1 | tee $PROGDIR/output.txt &
	CMDPID=$!
	export CMDPID

	# record own PID
	export PID=$$

	# Handler for signal USR1 for the timer
	trap handle_timeout SIGUSR1

	# starting timer in subshell. It sends a SIGUSR1 to the father if it timeouts.
	export TIMEOUT
	(sleep $TIMEOUT ; kill -SIGUSR1 $PID) &
	TPID=$!

	# wait for all production processes to finish
	wait ${CMDPID}

	if [ $? -ne 0 ]
	then
		# Timeout elapsed -> kill the process
		sleep 2
		ps aux|grep "/opt/mysql/sql/mysqld"|grep -v "safe"|grep -v grep|awk '{print $2}'|xargs kill -SIGSEGV > /dev/null
		sleep 2
		ps aux|grep "my"|grep -v grep|awk '{print $2}'|xargs kill -9 > /dev/null
		sleep 2
		kill -9 $CMDPID > /dev/null
		sleep 2

		touch killed.txt

		sleep 5
	else
		# kill timer
		kill $TPID
	fi



	echo "Saving logs"

	mkdir -p $SAVEDIR/$FAULT

	if [ -e $LOGDIR ]
	then
		cd $LOGDIR/..
		tar zcf $PROGDIR/log.tar.gz `basename $LOGDIR`
		cd - > /dev/null
	else
		echo "Log directory does not exist"
	fi

	mv $PROGDIR/log.tar.gz $SAVEDIR/$FAULT
	mv $PROGDIR/output.txt $SAVEDIR/$FAULT
	if [ -e $PROGDIR/killed.txt ]
	then
		mv $PROGDIR/killed.txt $SAVEDIR/$FAULT
	fi


done


echo "End campaign: "`date`

