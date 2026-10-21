package me.kavishdevar.librepods

import android.Manifest
import android.os.Looper
import android.telephony.TelephonyManager
import me.kavishdevar.librepods.utils.CallStateMonitor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33])
class CallStateMonitorTest {
    @Test fun permissionDenialDoesNotRegisterOrCrash() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.READ_PHONE_STATE)
        val events = mutableListOf<Int>()
        val monitor = CallStateMonitor(context) { events += it }
        monitor.start()
        shadowOf(context.getSystemService(TelephonyManager::class.java)).setCallState(
            TelephonyManager.CALL_STATE_RINGING)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(events.isEmpty())
        monitor.stop()
    }

    @Test fun callEventsArriveAndStopAfterCleanup() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val manager = context.getSystemService(TelephonyManager::class.java)
        val events = mutableListOf<Int>()
        val monitor = CallStateMonitor(context) { events += it }
        monitor.start()
        monitor.start() // Registration must be idempotent.
        shadowOf(Looper.getMainLooper()).idle()
        events.clear()
        shadowOf(manager).setCallState(TelephonyManager.CALL_STATE_RINGING)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(TelephonyManager.CALL_STATE_RINGING), events)
        monitor.stop()
        monitor.stop()
        events.clear()
        shadowOf(manager).setCallState(TelephonyManager.CALL_STATE_IDLE)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(events.isEmpty())
    }
}
