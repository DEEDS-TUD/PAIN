#!/bin/bash
# Script che avvia la fase di pulizia e quella di risoluzione degli errori
# richiamando gli appositi script
# Infine applicherà il tool di Fault Injection al file risultante
# Parametro: File .i da processare (da passare con o senza estensione)

FILE=`basename $1 .i`

if [ -f $FILE.i ]
then
	echo -e "\n\t- Cleaning script started\n"
	# Creating backup
	if [ ! -e "$FILE".i.bak ]
	then
		cp "$FILE".i "$FILE".i.bak
	fi
	perl -p -i -e ' while( s/(;|{)(.*?__label__.*?)(;|})/$1\/\* LABEL \*\/ $3/g ) {} ' "$FILE".i
	perl -p -i -e ' s/__builtin_constant_p//g ' "$FILE".i
	perl -p -i -e ' s/^(.*tick_device.*)/\/\/$1/ ; ' "$FILE".i
	perl -p -i -e ' s/^(.*typeof__\(struct tss_struct.*)/\/\/$1/ ; ' "$FILE".i
	# Removing anonymous struct
	perl ./anon_struct.pl "$FILE".i > "temp_new.i"
	# Removing temporary file 
	rm "$FILE".i && mv temp_new.i "$FILE".i
	echo -e "\t- Cleaning script done\n\n\t- Tool-related errors solving script started\n"
	./after_res.sh  "$FILE".i
	echo -e "\n\t- Tool-related errors solving script done\n\n\t- Fault Injection Tool launched\n"
	./injection "$FILE".i && mkdir -p "$FILE"_patch && mv "$FILE".i_O* ./"$FILE"_patch/ && echo "1" > success
	if [ -s "success" ]
	then
		rm success
		echo -e "\n\t-> All generated patch files were moved to directory \"$FILE""_patch\"\n";
	else
		echo -e "\n\t-> Something went wrong...\n\n";
		exit 1
	fi
else
	echo -e "\n\t-> ERRORE: The file doesn't exist!!! \n"

	exit 1
fi

exit 0
