#!/bin/sh

query() {
	echo "$*"|mysql -ugrinder -pgrinder grinder
}

#i=$(query 'select max(id) as id from testcases'|tail -1)

for f in $(find "$1" -name '*.ko'); do
	i=$((i + 1))
	query "insert testcases (\`bit\`, \`kservice\`, \`module\`, \`parameter\`) values (0, 'none', '$(basename $f)', 0);"
done
