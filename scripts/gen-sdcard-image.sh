#!/bin/bash
#
# Generator script for producing an empty FAT32 SD card image.
# Generated sdcard.img and vanilla_sdcard.img are stored in AFI_IMAGES_DIR.
#
# REQUIRES
#   - correct env setup (env.sh)
#

# little helper to script location
THIS_SCRIPT="${BASH_SOURCE[0]}"
THIS_SCRIPT_DIR="$(dirname $THIS_SCRIPT)"

# check env setup
[[ -z "$AFI_HOME" ]] && { \
  . "$THIS_SCRIPT_DIR/../env.sh" || \
    { echo "ERROR: Invalid environment setup."; exit 1; };
}

# local variables
SDCARD_SIZE="${SDCARD_SIZE-"30M"}"
SDIMG_OUT="$AFI_SDIMG"
SDIMG_VANILLA_OUT="$AFI_VSDIMG"

ensure_file() {
  [[ ! -f "$*" ]] && \
    echo "ERROR: File [$*] does not exist." && \
    exit 1
}


echo ""
echo "Android FI SD card image generator"
echo ""
echo "Target image:  $SDIMG_OUT"
echo "Vanilla image: $SDIMG_VANILLA_OUT"
echo "SD card size:  $SDCARD_SIZE"
echo ""

if [[ -f "$SDIMG_OUT" || -f "$SDIMG_VANILLA_OUT" ]] 
then
  echo "WARNING: Existing SD card image will be overwritten."
  read -p "Do you want to continue? (y/N) "
  [[ "$REPLY" != "y" ]] && echo "Aborted by user." && exit 0
  echo ""
fi

echo "Creating images..."
mksdcard "$SDCARD_SIZE" "$SDIMG_OUT"
ensure_file "$SDIMG_OUT"

echo "Creating vanilla image..."
cp "$SDIMG_OUT" "$SDIMG_VANILLA_OUT"
ensure_file "$SDIMG_VANILLA_OUT"

echo "Fin."

