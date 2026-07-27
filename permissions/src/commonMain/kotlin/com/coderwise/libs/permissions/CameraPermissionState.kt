package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Camera access — a single runtime permission on Android (no API-level gate; unlike
 * notifications it has needed a grant since API 23) and AVFoundation's video authorization
 * on iOS. On desktop/js there is no camera permission model, so [status] is always
 * [PermissionStatus.Granted].
 *
 * The host app declares what it uses: `android.permission.CAMERA` in its manifest, and
 * NSCameraUsageDescription in Info.plist. This library declares neither, so that apps
 * using another state here don't ship a camera permission they never ask for.
 */
@Stable
interface CameraPermissionState {
    val status: PermissionStatus
    fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit = {})
}

@Composable
expect fun rememberCameraPermissionState(): CameraPermissionState
