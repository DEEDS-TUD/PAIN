#!/bin/bash


CHECKDIR="/tmp/android-$USER"
CHECK_PERIOD="5m"
#FREE_THRES=10097152
FREE_THRES=2097152

cd "$CHECKDIR"

while true
do
  FREE=$(df . | awk 'END { print $4 }')
  if (( FREE < FREE_THRES ))
  then
    for fil in $(ls -c1)
    do
      if [[ -f "$fil" && $(fuser "$fil") != 0 ]]
      then
        echo "DEL: $fil"
        rm "$fil"
      else
        echo "NO: $fil"
      fi
    done
  fi
  sleep $CHECK_PERIOD
done



