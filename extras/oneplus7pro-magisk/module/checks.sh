#!/system/bin/sh
file_hash() {
    [ -f "$1" ] || return 1
    sha256sum "$1" 2>/dev/null | cut -d ' ' -f 1
}

check_conflicts() {
    CONFLICT=
    for OTHER in /data/adb/modules/* /data/adb/modules_update/*; do
        [ -d "$OTHER" ] || continue
        [ "${OTHER##*/}" = "$MODULE_ID" ] && continue
        [ -e "$OTHER/disable" ] && continue
        [ -e "$OTHER/remove" ] && continue
        # Be conservative even when another module temporarily uses skip_mount.
        if [ -e "$OTHER/$MODULE_FILE" ] || [ -e "$OTHER/system/lib64/libbluetooth_qti.so" ] ||
           [ -e "$OTHER/system/.replace" ] || [ -e "$OTHER/system/system_ext/.replace" ] ||
           [ -e "$OTHER/system/system_ext/lib64/.replace" ]; then
            CONFLICT=${OTHER##*/}
            return 1
        fi
    done
    return 0
}

