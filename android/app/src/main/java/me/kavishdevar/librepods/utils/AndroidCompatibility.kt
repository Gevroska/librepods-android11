package me.kavishdevar.librepods.utils

/** Pure SDK policy. Normal Bluetooth permissions stay in the manifest. */
object AndroidCompatibility {
    // Permission names are strings so this policy can also be tested without Android.
    fun bluetoothRuntimePermissions(sdk: Int): List<String> = if (sdk >= 31) {
        listOf("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE")
    } else {
        listOf("android.permission.ACCESS_FINE_LOCATION")
    }

    fun notificationRuntimePermissions(sdk: Int): List<String> = if (sdk >= 33) {
        listOf("android.permission.POST_NOTIFICATIONS")
    } else {
        emptyList()
    }
}
