package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState {
    val manager = remember { CLLocationManager() }
    val statusState = remember { mutableStateOf(currentStatus()) }
    val onResultCallback = remember { mutableStateOf<((PermissionStatus) -> Unit)?>(null) }

    val delegate = remember {
        LocationDelegate { newStatus ->
            statusState.value = newStatus
            onResultCallback.value?.invoke(newStatus)
            onResultCallback.value = null
        }
    }

    DisposableEffect(manager, delegate) {
        manager.delegate = delegate
        onDispose { manager.delegate = null }
    }

    return remember(manager) {
        object : LocationPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                val current = currentStatus()
                if (current is PermissionStatus.Granted) {
                    onResult(current)
                    return
                }
                if (current is PermissionStatus.Denied && !current.shouldShowRationale) {
                    // Already decided (denied/restricted) — requestWhenInUseAuthorization
                    // would silently no-op, so send the user to Settings instead.
                    openIosAppSettings()
                    onResult(current)
                    return
                }

                onResultCallback.value = onResult
                manager.requestWhenInUseAuthorization()
            }
        }
    }
}

private class LocationDelegate(
    private val onStatusChanged: (PermissionStatus) -> Unit
) : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onStatusChanged(currentStatus())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun currentStatus(): PermissionStatus =
    when (CLLocationManager.authorizationStatus()) {
        kCLAuthorizationStatusAuthorizedAlways,
        kCLAuthorizationStatusAuthorizedWhenInUse -> PermissionStatus.Granted
        // Not yet decided — a system prompt can still be shown.
        kCLAuthorizationStatusNotDetermined -> PermissionStatus.Denied(shouldShowRationale = true)
        // Denied or restricted — iOS won't prompt again; Settings is the only path.
        else -> PermissionStatus.Denied(shouldShowRationale = false)
    }
