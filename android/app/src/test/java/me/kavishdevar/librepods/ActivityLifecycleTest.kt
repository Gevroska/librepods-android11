package me.kavishdevar.librepods

import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33])
class ActivityLifecycleTest {
    @Test fun backgroundResumeAndDestroyDoNotReleaseUnownedReceiversOrBindings() {
        ShadowLog.clear()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity.pause().stop().restart().start().resume()
        activity.pause().stop().destroy()
        assertTrue(ShadowLog.getLogsForTag("MainActivity").none { it.type >= Log.ERROR })
    }
}
