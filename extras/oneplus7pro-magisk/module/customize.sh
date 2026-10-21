#!/system/bin/sh
# This installer modifies only a copy in the Magisk module directory.
[ "$BOOTMODE" = true ] || abort "Install from the Magisk app while Android is running."
[ "$ARCH" = arm64 ] || abort "This patch requires arm64."
[ "$API" = 30 ] || abort "This patch was built for this phone's Android 11 ROM only."
[ -f "$MODPATH/patch.conf" ] || abort "Patch metadata is missing."
. "$MODPATH/patch.conf"
. "$MODPATH/checks.sh"

ui_print "OnePlus 7 Pro Bluetooth compatibility patch"
ui_print "Unofficial, device-specific build for LibrePods"
[ "$(getprop ro.product.device)" = "$EXPECTED_DEVICE" ] || abort "Wrong device."
[ "$(getprop ro.product.model)" = "$EXPECTED_MODEL" ] || abort "Wrong model."
check_conflicts "$MODPATH" || abort "Another enabled module replaces the Bluetooth library: $CONFLICT"
SOURCE_SHA256=$(file_hash "$TARGET")
case "$SOURCE_SHA256" in
    "$ORIGINAL_SHA256"|"$PREVIOUS_SHA256"|"$PATCHED_SHA256") ;;
    *) abort "Unknown Bluetooth library checksum. This exact-ROM patch cannot be installed." ;;
esac
[ "$(file_hash "$MODPATH/patch.bin")" = "$PATCH_SHA256" ] || abort "Patch payload checksum differs."

DEST="$MODPATH/$MODULE_FILE"
mkdir -p "${DEST%/*}" || abort "Cannot create module directory."
cp "$TARGET" "$DEST" || abort "Cannot copy original Bluetooth library."
# Updates may see an existing Magisk overlay. Restore only our known patch in
# the copied file, then require the complete original ROM checksum again.
if [ "$SOURCE_SHA256" = "$PREVIOUS_SHA256" ]; then
    dd if="$MODPATH/previous-original.bin" of="$DEST" bs=1 seek="$PREVIOUS_OFFSET" conv=notrunc 2>/dev/null || abort "Cannot restore previous patch in copied library."
elif [ "$SOURCE_SHA256" = "$PATCHED_SHA256" ]; then
    dd if="$MODPATH/current-original.bin" of="$DEST" bs=1 seek="$PATCH_OFFSET" conv=notrunc 2>/dev/null || abort "Cannot restore current patch in copied library."
fi
[ "$(file_hash "$DEST")" = "$ORIGINAL_SHA256" ] || abort "Copied library did not restore to the exact original ROM checksum."
dd if="$MODPATH/patch.bin" of="$DEST" bs=1 seek="$PATCH_OFFSET" conv=notrunc 2>/dev/null || abort "Cannot apply patch."
[ "$(file_hash "$DEST")" = "$PATCHED_SHA256" ] || abort "Patched library checksum differs."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$DEST" "$FILE_UID" "$FILE_GID" "$FILE_MODE" "$FILE_CONTEXT"
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
# The boot guard must approve the original and patched files before mounting.
touch "$MODPATH/skip_mount" || abort "Cannot create boot safety flag."
ui_print "Verified original and patched library checksums."
ui_print "The system partition was not modified. Reboot to activate."
ui_print "AirPods Pro 3 and app compatibility still require a device test."

