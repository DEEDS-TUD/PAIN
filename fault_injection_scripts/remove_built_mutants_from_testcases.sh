#!/bin/sh

query() {
	echo "$*"|mysql -ugrinder -pgrinder grinder
}

for f in $(find "$1" -name '*.ko'); do
	query "delete from testcases where module='$(basename "$f")'"
done
