#! /bin/bash
#
# Start AFI GRINDER from this directory.
# Command line arguments to this script are forwarded to the GRINDER application.
#

# little helper to script location
THIS_SCRIPT="${BASH_SOURCE[0]}"
THIS_SCRIPT_DIR="$(dirname $THIS_SCRIPT)"

# check env setup
[[ -z "$AFI_HOME" ]] && { \
  . "$THIS_SCRIPT_DIR/../env.sh" || \
  { echo "ERROR: Invalid environment setup."; exit 1; };
}

GRINDER_JAR='../GRINDER/client/core/target/grinder.client.core-0.0.1-SNAPSHOT.jar'

java \
  -jar "$GRINDER_JAR" \
  $@

