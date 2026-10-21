package me.kavishdevar.librepods.presentation.screens.onboarding

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.AppInfoCard
import me.kavishdevar.librepods.presentation.components.DeviceInfoCard
import me.kavishdevar.librepods.presentation.components.StyledListItem

@Composable
fun NotSupportedPage(
    bypassCompatibilityCheck: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(42.dp)
            )
            .clip(RoundedCornerShape(42.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier)
            Text(
                text = if (Build.VERSION.SDK_INT <= 32) "A Bluetooth compatibility patch is required on this device."
                    else stringResource(R.string.not_compatible_update),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (Build.VERSION.SDK_INT <= 32) "This fork supports Android 11, but the system Bluetooth limitation still needs a compatible patch. Install your device-specific Magisk module and reboot before continuing. This screen cannot verify that the patch is active."
                    else stringResource(R.string.not_compatitble_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            DeviceInfoCard()
            AppInfoCard()
            if (!BuildConfig.PLAY_BUILD) {
                StyledListItem(
                    name = if (Build.VERSION.SDK_INT <= 32) "I have installed the Bluetooth patch"
                        else stringResource(R.string.bypass_compatibility_check),
                    onClick = bypassCompatibilityCheck
                )
            }
            Spacer(modifier = Modifier)
        }
    }
}
