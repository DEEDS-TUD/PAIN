#!/bin/bash
#
# Creates teh required amount of AVDs from a given template AVD.
# Only configuration files are copied, no image files. The file contents are modified
# to contain the correct paths and names for each  copied AVD.
#
# Invocation: setup-avds.sh <template AVD> <number of final AVDs>
#   <emulator device> is optional, when given it must be a valid emulator name
#   <shell commands> valid shell commands
#


# little helper to script location
THIS_SCRIPT="${BASH_SOURCE[0]}"
THIS_SCRIPT_DIR="$(dirname $THIS_SCRIPT)"

# check env setup
[[ -z "$AFI_HOME" ]] && { \
  . "$THIS_SCRIPT_DIR/../env.sh" || \
  { echo "ERROR: Invalid environment setup."; exit 1; };
}

print_help() {
  echo "Invocation: $THIS_SCRIPT <AVD template> <#copies>"
  echo ""
  echo "<AVD template>  Name of existing AVD that serves as template."
  echo "<#copies>       Number of AVD copies to create from template."
}

# command line arguments, 2 are expected
TEMPLATE_AVD_NAME="$1"
NUM_COPIES="$2"

# some arguments checks
if [[ -z "$TEMPLATE_AVD_NAME" ]]
then
  echo "ERROR: Missing template AVD name."
  print_help
  exit 1
fi
if [[ "$NUM_COPIES" != +([[:digit:]]) ]]
then
  echo "ERROR: Invalid number of AVDs."
  print_help
  exit 1
fi

TEMPLATE_AVD_DIR="$ANDROID_AVD_DIR/${TEMPLATE_AVD_NAME}.avd"
TEMPLATE_INI="$ANDROID_AVD_DIR/${TEMPLATE_AVD_NAME}.ini"

echo "Template AVD dir:      $TEMPLATE_AVD_DIR"
echo "Template AVD settings: $TEMPLATE_INI"

# does template exist
if [[ ! -d "$TEMPLATE_AVD_DIR" ]]
then
  echo "ERROR: Cannot find template AVD directory:"
  echo "       $TEMPLATE_AVD_DIR"
  exit 1
fi
if [[ ! -f "$TEMPLATE_INI" ]]
then
  echo "ERROR: Cannot find template AVD settings file:"
  echo "       $TEMPLATE_INI"
  exit 1
fi

(( i = 0 ))
while (( i < NUM_COPIES ))
do
  TARGET_AVD_NAME="${TEMPLATE_AVD_NAME}-$i"
  echo "Creating $TARGET_AVD_NAME"

  TARGET_INI="$ANDROID_AVD_DIR/${TARGET_AVD_NAME}.ini"
  TARGET_DIR="$ANDROID_AVD_DIR/${TARGET_AVD_NAME}.avd"

  [[ -f "$TARGET_INI" || -d "$TARGET_DIR" ]] && \
    echo "WARNING: Existing AVD will be overwritten."

  SED_SUB_PATH="s@path=.*@path=${TARGET_DIR}@"
  SED_SUB_RELPATH="s@path\.rel=.*@path\.rel=avd/${TARGET_AVD_NAME}.avd@"
#  echo $TARGET_DIR
#  echo $SED_SUB_PATH
  sed -e "$SED_SUB_PATH" -e "$SED_SUB_RELPATH" "$TEMPLATE_INI" > "$TARGET_INI"

  mkdir -p "$TARGET_DIR"
  cp "${TEMPLATE_AVD_DIR}"/*.ini "$TARGET_DIR"
  SED_SUB_AVD_NAME="s@avd\.name =.*@avd\.name = ${TARGET_AVD_NAME}@"
  sed -e "$SED_SUB_AVD_NAME" "$TEMPLATE_AVD_DIR/hardware-qemu.ini" \
    > "$TARGET_DIR/hardware-qemu.ini"

  (( ++i ))
done

echo "Fin."

