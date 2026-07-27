package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * While-in-use location access — see [BackgroundLocationPermissionState] for "always".
 *
 * The host app declares what it uses: `android.permission.ACCESS_FINE_LOCATION` and/or
 * `ACCESS_COARSE_LOCATION` in its manifest, and NSLocationWhenInUseUsageDescription in
 * Info.plist. This library declares neither, so that apps using another state here don't
 * ship a location permission they never ask for.
 */
@Stable
interface LocationPermissionState {
    val status: PermissionStatus
    fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit = {})
}

@Composable
expect fun rememberLocationPermissionState(): LocationPermissionState
