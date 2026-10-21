package me.kavishdevar.librepods

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import me.kavishdevar.librepods.presentation.screens.onboarding.PermissionsPage
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.utils.AndroidCompatibility
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PermissionsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun showPermissions() {
        compose.setContent {
            LibrePodsTheme(m3eEnabled = true) { PermissionsPage({}, {}) }
        }
    }

    @Test fun missingBluetoothPermissionPreventsContinuing() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            *AndroidCompatibility.bluetoothRuntimePermissions(Build.VERSION.SDK_INT).toTypedArray())
        showPermissions()
        compose.onNodeWithContentDescription("forward").assertIsNotEnabled()
    }

    @Test fun bluetoothPermissionAllowsContinuingWithoutOptionalPermissions() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            *AndroidCompatibility.bluetoothRuntimePermissions(Build.VERSION.SDK_INT).toTypedArray())
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_PHONE_STATE)
        showPermissions()
        compose.onNodeWithContentDescription("forward").assertIsEnabled()
    }
}
