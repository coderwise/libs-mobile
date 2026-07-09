package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Android's "always" location access — distinct from [LocationPermissionState],
 * which covers while-in-use. Must be requested separately, after while-in-use
 * is already granted, and only matters when something needs to start location
 * work from a background context (e.g. a foreground service triggered by a
 * broadcast). Not a concern on any other platform today — [status] is always
 * [PermissionStatus.Granted] there.
 */
@Stable
interface BackgroundLocationPermissionState {
    val status: PermissionStatus
    fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit = {})
}

@Composable
expect fun rememberBackgroundLocationPermissionState(): BackgroundLocationPermissionState
