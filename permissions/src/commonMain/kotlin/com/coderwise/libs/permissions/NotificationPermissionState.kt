package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * POST_NOTIFICATIONS — a runtime permission on API 33+ (Android 13); below
 * that, and on every other platform, notifications don't need a runtime grant
 * so [status] is always [PermissionStatus.Granted].
 */
@Stable
interface NotificationPermissionState {
    val status: PermissionStatus
    fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit = {})
}

@Composable
expect fun rememberNotificationPermissionState(): NotificationPermissionState
