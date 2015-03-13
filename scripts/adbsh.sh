#!/bin/bash
#
# Wrapper script for 'adb shell' command that retrieves the exit code of the executed
# shell command and returns it as own exit code.
# Note that this script adds the exit code to the stdout output of the executed shell
# command.
#
# Invocation: adbshell.sh [<emulator device>] <shell commands ...>
#   <emulator device> is optional, when given it must be a valid emulator name
#   <shell commands> valid shell commands
#

# check script invocation - is an emulator device specified?
if [[ "$1" == emulator-+([[:digit:]]) ]]
then
  EMU="-s $1"
  shift
fi

ADB="adb"
CMD_IN="$*"
CMD_ADD="echo \$?"
CMD="$CMD_IN; $CMD_ADD"

# redirect fd5 to stdout and use it to copy output to both, the variable and the console
exec 5>&1
CMD_RET=$($ADB $EMU shell "$CMD" | tee /dev/fd/5 | tail -1; exit ${PIPESTATUS[0]})
RET=$?
# close fd5
exec 5>&-

[[ $RET != 0 ]] && \
  echo "ERROR: ADB returned an error: $RET" && \
  exit 42

CMD_RET="${CMD_RET%?}" # remove trailing endline
[[ "$CMD_RET" != +([[:digit:]]) ]] && \
  echo "ERROR: invalid exit code: [$CMD_RET]" && \
  exit 43

#echo "SH-RET: $CMD_RET"
exit $CMD_RET

