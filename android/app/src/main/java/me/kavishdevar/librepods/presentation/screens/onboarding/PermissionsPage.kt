package me.kavishdevar.librepods.presentation.screens.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import me.kavishdevar.librepods.presentation.MaterialIcons
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.utils.AndroidCompatibility

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsPage(
    onBackward: () -> Unit,
    onForward: () -> Unit
) {

    var grantingAll by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val phonePermissionState = rememberMultiplePermissionsState(
        listOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.ANSWER_PHONE_CALLS"
        )
    ) {
        if (grantingAll) {
            if (!canDrawOverlays.value) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()
                )
                context.startActivity(intent)
            }
        }
    }


    val notificationPermissionsState = rememberMultiplePermissionsState(
        AndroidCompatibility.notificationRuntimePermissions(Build.VERSION.SDK_INT)
    ) {
        if (grantingAll) {
            if (!phonePermissionState.allPermissionsGranted) phonePermissionState.launchMultiplePermissionRequest()
            else if (!canDrawOverlays.value) canDrawOverlays.value = Settings.canDrawOverlays(context)
        }
    }


    val bluetoothPermissionsState = rememberMultiplePermissionsState(
        AndroidCompatibility.bluetoothRuntimePermissions(Build.VERSION.SDK_INT)
    ) {
        if (grantingAll) {
            if (!notificationPermissionsState.allPermissionsGranted) notificationPermissionsState.launchMultiplePermissionRequest()
            else if (!phonePermissionState.allPermissionsGranted) phonePermissionState.launchMultiplePermissionRequest()
            else if (!canDrawOverlays.value) canDrawOverlays.value = Settings.canDrawOverlays(context)
        }
    }

    val legacyScanning = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val locationManager = context.getSystemService(LocationManager::class.java)
    var locationEnabled by remember { mutableStateOf(locationManager.isLocationEnabled) }
    var backgroundLocationGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays.value = Settings.canDrawOverlays(context)
                locationEnabled = locationManager.isLocationEnabled
                backgroundLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier.background(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(42.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StyledList(title = "Required Permissions") {
                val animatedBluetoothIconColor by animateColorAsState(if (bluetoothPermissionsState.allPermissionsGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                val animatedBluetoothContainerColor by animateColorAsState(
                    if (bluetoothPermissionsState.allPermissionsGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                )

                StyledListItem(
                    name = "Bluetooth",
                    onClick = if (!bluetoothPermissionsState.allPermissionsGranted) {
                        {
                            grantingAll = false
                            bluetoothPermissionsState.launchMultiplePermissionRequest()
                        }
                    } else null,
                    description = if (legacyScanning) "Android 11 requires Location permission to discover nearby AirPods. LibrePods does not collect your location."
                        else "Required to communicate with AirPods",
                    orientation = ListItemOrientation.Vertical,
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    animatedBluetoothContainerColor,
                                    MaterialShapes.SoftBurst.normalized()
                                        .toShape()
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MaterialIcons.bluetooth,
                                contentDescription = "bluetooth",
                                modifier = Modifier.size(24.dp),
                                tint = animatedBluetoothIconColor
                            )
                        }
                    },
                )
            }
            if (legacyScanning) {
                StyledListItem(
                    name = "Location services",
                    description = if (locationEnabled) "Enabled. Android 11 requires this switch for Bluetooth discovery."
                        else "Turn on Location in system settings so Android 11 can discover nearby AirPods.",
                    onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    orientation = ListItemOrientation.Vertical
                )
                StyledListItem(
                    name = "Background discovery (optional)",
                    description = if (backgroundLocationGranted) "Allowed. AirPods can be discovered while the app is in the background."
                        else "For discovery while the app is closed, open Permissions > Location and select Allow all the time. First grant the Bluetooth discovery permission above.",
                    onClick = if (bluetoothPermissionsState.allPermissionsGranted) {
                        { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
                    } else null,
                    orientation = ListItemOrientation.Vertical
                )
            }
            StyledList(title = "Optional Permissions") {
                val animatedNotificationsIconColor by animateColorAsState(
                    if (notificationPermissionsState.allPermissionsGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                val animatedNotificationsContainerColor by animateColorAsState(
                    if (notificationPermissionsState.allPermissionsGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                val animatedPhoneIconColor by animateColorAsState(if (phonePermissionState.allPermissionsGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                val animatedPhoneContainerColor by animateColorAsState(
                    if (phonePermissionState.allPermissionsGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                )

                StyledListItem(
                    name = "Notifications",
                    onClick = if (!notificationPermissionsState.allPermissionsGranted) {
                        {
                            grantingAll = false
                            notificationPermissionsState.launchMultiplePermissionRequest()
                        }
                    } else null,
                    description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Show battery status"
                        else "Available without a runtime permission on this Android version",
                    orientation = ListItemOrientation.Vertical,
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    animatedNotificationsContainerColor,
                                    MaterialShapes.SoftBurst.normalized()
                                        .toShape()
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MaterialIcons.notifications,
                                contentDescription = "notifications",
                                modifier = Modifier.size(24.dp),
                                tint = animatedNotificationsIconColor
                            )
                        }
                    },
                )
                StyledListItem(
                    name = "Phone",
                    onClick = if (!phonePermissionState.allPermissionsGranted) {
                        {
                            grantingAll = false
                            phonePermissionState.launchMultiplePermissionRequest()
                        }
                    } else null,
                    description = "Respond to phone calls with head gestures",
                    orientation = ListItemOrientation.Vertical,
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    animatedPhoneContainerColor,
                                    MaterialShapes.SoftBurst.normalized()
                                        .toShape()
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MaterialIcons.call,
                                contentDescription = "bluetooth",
                                modifier = Modifier.size(24.dp),
                                tint = animatedPhoneIconColor
                            )
                        }
                    },
                )
            }

            val animatedOverlayIconColor by animateColorAsState(if (canDrawOverlays.value) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            val animatedOverlayContainerColor by animateColorAsState(if (canDrawOverlays.value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)

            StyledListItem(
                name = "Display over other apps",
                onClick = if (!canDrawOverlays.value) {
                    {
                        grantingAll = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                        context.startActivity(intent)
                    }
                } else null,
                description = "Show popups when AirPods are nearby or audio switches to them.",
                orientation = ListItemOrientation.Vertical,
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                animatedOverlayContainerColor,
                                MaterialShapes.SoftBurst.normalized()
                                    .toShape()
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MaterialIcons.stack,
                            contentDescription = "bluetooth",
                            modifier = Modifier.size(24.dp),
                            tint = animatedOverlayIconColor
                        )
                    }
                },
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledIconButton(
                    onClick = onBackward,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Narrow)),
                    shape = IconButtonDefaults.mediumRoundShape
                ) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "backward",
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }
                Button(
                    onClick = {
                        grantingAll = true
                        if (!bluetoothPermissionsState.allPermissionsGranted) bluetoothPermissionsState.launchMultiplePermissionRequest()
                        else if (!notificationPermissionsState.allPermissionsGranted) notificationPermissionsState.launchMultiplePermissionRequest()
                        else if (!phonePermissionState.allPermissionsGranted) phonePermissionState.launchMultiplePermissionRequest()
                        else if (!canDrawOverlays.value) canDrawOverlays.value =
                            Settings.canDrawOverlays(context)
                    },
                    modifier = Modifier
                        .height(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Narrow).height)
                        .weight(1f),
                    enabled = !bluetoothPermissionsState.allPermissionsGranted || !notificationPermissionsState.allPermissionsGranted || !phonePermissionState.allPermissionsGranted || !canDrawOverlays.value
                ) {
                    Text(
                        text = "Grant all",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                FilledIconButton(
                    onClick = onForward,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(
                            IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Narrow)
                        ),
                    shape = IconButtonDefaults.mediumRoundShape,
                    enabled = bluetoothPermissionsState.allPermissionsGranted
                ) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowForward,
                        contentDescription = "forward",
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }
            }
        }
    }
}
