package me.kavishdevar.librepods

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.os.ParcelUuid
import me.kavishdevar.librepods.bluetooth.createBluetoothSocket
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Checks framework constructor compatibility only; no socket is connected. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33])
class BluetoothSocketCompatibilityTest {
    @Test fun classicL2capConstructorPreservesTheAirPodsControlChannel() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter.getRemoteDevice("00:11:22:33:44:55")
        val uuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
        createBluetoothSocket(adapter, device, uuid, 0x1001).use { socket ->
            assertEquals(BluetoothSocket.TYPE_L2CAP, socket.connectionType)
            assertEquals(device, socket.remoteDevice)
            assertEquals(0x1001, ReflectionHelpers.getField<Int>(socket, "mPort"))
        }
    }
}
