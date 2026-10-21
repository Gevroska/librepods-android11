package me.kavishdevar.librepods

import android.content.Context
import android.os.Looper
import me.kavishdevar.librepods.services.AirPodsService
import me.kavishdevar.librepods.services.ServiceManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSettings
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], shadows = [ConnectionIslandTest.NoNativeSocketLoader::class],
    instrumentedPackages = ["me.kavishdevar.librepods.services"])
class ConnectionIslandTest {
    @Implements(AirPodsService::class)
    class NoNativeSocketLoader {
        companion object {
            // The Android Bluetooth JNI library cannot load on the host JVM.
            // Only its static loader is replaced; service and overlay methods remain real.
            @JvmStatic @Implementation fun __staticInitializer__() = Unit
        }
    }

    private lateinit var service: AirPodsService

    @Before fun prepare() {
        // Attach a real service context without starting radios or other background services.
        service = Robolectric.buildService(AirPodsService::class.java).get()
        val preferences = service.getSharedPreferences("settings", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("automatic_ear_detection", false)
            .putBoolean("show_island_popup", true).commit()
        AirPodsService::class.java.getDeclaredField("sharedPreferences").apply {
            isAccessible = true
            set(service, preferences)
        }
        AirPodsService::class.java.getDeclaredField("config").apply {
            isAccessible = true
            set(service, AirPodsService.ServiceConfig(earDetectionEnabled = false))
        }
        ServiceManager.setService(service)
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After fun cleanup() {
        if (::service.isInitialized) service.islandWindow?.forceClose()
        shadowOf(Looper.getMainLooper()).idle()
        ServiceManager.setService(null)
    }

    @Test fun connectionShowsWithoutEarAutomationAndCanShowAgainAfterDismissal() {
        service.showConnectedIsland()
        shadowOf(Looper.getMainLooper()).idle()
        val first = service.islandWindow
        assertTrue(first?.isVisible == true)
        service.showConnectedIsland()
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(first, service.islandWindow)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertFalse(first!!.isVisible)
        assertFalse(service.islandOpen)
        service.showConnectedIsland()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(service.islandWindow?.isVisible == true)
        assertNotSame(first, service.islandWindow)
        assertFalse(service.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("automatic_ear_detection", true))
    }

    @Test fun popupPreferenceAndOverlayPermissionAreStillRespected() {
        val preferences = service.getSharedPreferences("settings", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("show_island_popup", false).commit()
        service.showConnectedIsland()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(service.islandWindow)
        preferences.edit().putBoolean("show_island_popup", true).commit()
        ShadowSettings.setCanDrawOverlays(false)
        service.showConnectedIsland()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(service.islandWindow)
    }
}
