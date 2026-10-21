#!/system/bin/sh
MODDIR=${0%/*}
umask 022

fail_closed() {
    touch "$MODDIR/skip_mount" "$MODDIR/disable"
    printf '%s\n' "DISABLED: $1" > "$MODDIR/boot-status.txt"
    exit 0
}

# Magisk executes this before its module overlays are mounted.
touch "$MODDIR/skip_mount" || exit 1
[ -r "$MODDIR/patch.conf" ] || fail_closed "Missing patch metadata"
[ -r "$MODDIR/checks.sh" ] || fail_closed "Missing checks"
. "$MODDIR/patch.conf"
. "$MODDIR/checks.sh"

[ "$(getprop ro.product.device)" = "$EXPECTED_DEVICE" ] || fail_closed "Device mismatch"
[ "$(getprop ro.product.model)" = "$EXPECTED_MODEL" ] || fail_closed "Model mismatch"
[ "$(getprop ro.build.version.sdk)" = 30 ] || fail_closed "Android version changed"
check_conflicts "$MODDIR" || fail_closed "Conflicting module: $CONFLICT"
[ "$(file_hash "$TARGET")" = "$ORIGINAL_SHA256" ] || fail_closed "Original Bluetooth library changed or is unavailable"
[ "$(file_hash "$MODDIR/$MODULE_FILE")" = "$PATCHED_SHA256" ] || fail_closed "Module library changed or is unavailable"

rm -f "$MODDIR/skip_mount" || fail_closed "Cannot enable module mount"
printf '%s\n' "APPROVED: Original and module library checksums match. Mounting and Bluetooth operation require runtime verification." > "$MODDIR/boot-status.txt"
