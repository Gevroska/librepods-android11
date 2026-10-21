package me.kavishdevar.librepods

import android.os.Looper
import me.kavishdevar.librepods.utils.LatestUpdateDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LatestUpdateDispatcherTest {
    @Test fun burstPublishesTheLatestBatteryWithoutFloodingNotifications() {
        val delivered = mutableListOf<Int>()
        val dispatcher = LatestUpdateDispatcher<Int>(publish = { delivered.add(it) })
        dispatcher.submit(60)
        shadowOf(Looper.getMainLooper()).idle()
        repeat(100) { dispatcher.submit(61 + it) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(499))
        assertEquals(listOf(60), delivered)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(listOf(60, 160), delivered)
        dispatcher.submit(160)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(listOf(60, 160), delivered)
    }

    @Test fun disconnectCancelsPendingUpdateAndAllowsSameContentOnReconnect() {
        val delivered = mutableListOf<Int>()
        val dispatcher = LatestUpdateDispatcher<Int>(publish = { delivered.add(it) })
        dispatcher.submit(60)
        shadowOf(Looper.getMainLooper()).idle()
        dispatcher.submit(61)
        dispatcher.reset()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(listOf(60), delivered)
        dispatcher.submit(60)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(60, 60), delivered)
        dispatcher.submit(62)
        dispatcher.close()
        dispatcher.submit(63)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(listOf(60, 60), delivered)
    }
}
