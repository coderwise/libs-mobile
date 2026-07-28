package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * "Always" location access — distinct from [LocationPermissionState], which
 * covers while-in-use. Must be requested separately, after while-in-use is
 * already granted, and only matters when something needs to start location work
 * from a background context: a foreground service triggered by a broadcast on
 * Android, an app launched into the background by a CarPlay scene on iOS.
 *
 * Both mobile platforms implement it for real. Elsewhere (desktop, JS) there is
 * nothing to escalate and [status] is always [PermissionStatus.Granted].
 *
 * The host app declares what it needs: `android.permission.ACCESS_BACKGROUND_LOCATION`
 * in its manifest, `NSLocationAlwaysAndWhenInUseUsageDescription` in Info.plist. This
 * library declares nothing, so that apps using another state here don't ship a location
 * permission they never ask for.
 */
@Stable
interface BackgroundLocationPermissionState {
    val status: PermissionStatus
    fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit = {})
}

@Composable
expect fun rememberBackgroundLocationPermissionState(): BackgroundLocationPermissionState
