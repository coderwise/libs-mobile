package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * POST_NOTIFICATIONS — a runtime permission on API 33+ (Android 13); below
 * that it doesn't need a runtime grant, so [status] is always
 * [PermissionStatus.Granted]. On iOS this maps to UNUserNotificationCenter
 * authorization ([status] is async there: it reads as Denied until the first
 * settings fetch completes). On desktop/js notifications need no runtime
 * grant today, so [status] is always [PermissionStatus.Granted].
 */
@Stable
interface NotificationPermissionState {
    val status: PermissionStatus
    fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit = {})
}

@Composable
expect fun rememberNotificationPermissionState(): NotificationPermissionState
