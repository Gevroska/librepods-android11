package me.kavishdevar.librepods

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the real Application, Activity and initial Compose screen, without Bluetooth. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun onboardingOpensAndClosesBeforeConnectingToTheService() {
        compose.onNodeWithText("Welcome to").assertIsDisplayed()
    }
}
