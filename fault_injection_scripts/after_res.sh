#!/bin/bash
# Script di risoluzione errori restituiti dal tool di fault injection o dal suo parser
# Parametro: File .i da controllare

FILEI=$1
FILER=temp_ris.txt
FILEE=temp_err.txt
TEMP=temp.txt

#./injection $FILEI > $FILER 2> $FILEE
./injection $FILEI >$FILER 2>&1

# Correggiamo eventuali errori residui restituiti dal parser del tool
J=0
touch $TEMP
if [ -s $FILER ]
then
	echo -e "\t\tRicerca errori restituiti dal parser del tool"
	while [ -s $FILER ]
	do 
	    #!sw! echo while iteration $J
	    perl err_parser.pl $FILER $FILEI
		#./injection $FILEI > $FILER 2> $FILEE
		#./injection $FILEI > $FILER 2>&1
		./injection $FILEI 2>&1 | egrep "error|warning|:[0-9]+:[0-9]+:" > $FILER
		grep 'Target file:' $FILER > $TEMP
		if [ -s $TEMP ]
		then
			rm $FILER
		#!sw! else
		    #!sw! echo temp is not deleted!
		fi
		J=$(($J+1))
	done
fi
# Correggiamo eventuali errori trovati dal tool di fault injection
touch $FILER
if [ -s $FILEE ]
then
	echo -e "\t\tRicerca errori restituiti dal tool"
	perl err_tool.pl $FILEE $FILEI
fi

# Pulizia dei file temporanei generati dallo script
rm -f $FILER $FILEE $TEMP

exit 0
