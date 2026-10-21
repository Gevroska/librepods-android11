package me.kavishdevar.librepods

import me.kavishdevar.librepods.bluetooth.AACPManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import android.util.Log
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AacpFrameTest {
    private fun controlFrame(size: Int, metadata: Boolean = false): ByteArray = ByteArray(size).apply {
        byteArrayOf(4, 0, 4, 0, 0x17, 0, 0, if (metadata) 4 else 0, if (metadata) 0 else 0x10, 0,
            (size - 12).toByte(), 0).copyInto(this)
    }

    @Test fun statusFramesAreNotFedToTheMotionParserAndMalformedFramesRemainWarnings() {
        val calls = mutableListOf<String>()
        val manager = AACPManager()
        manager.setPacketCallback(Proxy.newProxyInstance(
            AACPManager.PacketCallback::class.java.classLoader,
            arrayOf(AACPManager.PacketCallback::class.java)
        ) { _, method, _ -> calls.add(method.name); null } as AACPManager.PacketCallback)
        ShadowLog.clear()
        manager.receivePacket(controlFrame(21))
        manager.receivePacket(controlFrame(64, metadata = true))
        manager.receivePacket(byteArrayOf(1, 0, 4, 0) + ByteArray(14))
        assertEquals(List(3) { "onUnknownPacketReceived" }, calls)
        assertTrue(ShadowLog.getLogs().none { it.type >= Log.WARN })
        manager.receivePacket(controlFrame(21).copyOf(20))
        assertTrue(ShadowLog.getLogs().any { it.type == Log.WARN && it.msg.contains("Unrecognized short") })
        manager.receivePacket(byteArrayOf(4, 0, 4, 0, 0x17, 0) + ByteArray(64))
        assertEquals("onHeadTrackingReceived", calls.last())
    }
}
