package me.kavishdevar.librepods

import android.Manifest
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.data.AirPodsPro3
import me.kavishdevar.librepods.data.Capability
import me.kavishdevar.librepods.utils.AndroidCompatibility
import org.junit.Assert.*
import org.junit.Test

class AndroidCompatibilityTest {
    @Test fun android11RequestsLocationInsteadOfUnknownNearbyPermissions() {
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            AndroidCompatibility.bluetoothRuntimePermissions(30))
        assertTrue(AndroidCompatibility.notificationRuntimePermissions(30).isEmpty())
    }

    @Test fun android12UsesNearbyPermissionsWithoutNotificationPrompt() {
        for (sdk in listOf(31, 32)) {
            assertEquals(setOf(Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE),
                AndroidCompatibility.bluetoothRuntimePermissions(sdk).toSet())
            assertTrue(AndroidCompatibility.notificationRuntimePermissions(sdk).isEmpty())
        }
    }

    @Test fun android13RequestsNotificationsAndNearbyDevices() {
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS),
            AndroidCompatibility.notificationRuntimePermissions(33))
        assertFalse(AndroidCompatibility.bluetoothRuntimePermissions(33)
            .contains(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    @Test fun allUpstreamPro3ModelNumbersKeepTheirCapabilities() {
        for (number in listOf("A3063", "A3064", "A3065")) {
            val model = AirPodsModels.getModelByModelNumber(number)
            assertTrue(model is AirPodsPro3)
            assertTrue(model!!.capabilities.containsAll(setOf(Capability.LISTENING_MODE,
                Capability.HEAD_GESTURES, Capability.HEARING_AID, Capability.HRM,
                Capability.ADAPTIVE_AUDIO, Capability.CONVERSATION_AWARENESS)))
        }
    }
}
