#!/bin/bash
#

INFILE="$*"
OUTFILE="filtered_${INFILE}"

echo "Pre-Filtering ${INFILE}..."
grep \
  -Ev \
  'Forwarded:[[:space:]]*(hw\.|qtaguid:|binder:|emulator:|goldfish_new_pdev|goldfish_pdev_worker)' \
  "$INFILE" \
  > "$OUTFILE"

