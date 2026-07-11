package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberNotificationPermissionState(): NotificationPermissionState {
    // Settings lookup is async on iOS; start pessimistic until the first fetch lands.
    val statusState = remember { mutableStateOf<PermissionStatus>(PermissionStatus.Denied(shouldShowRationale = false)) }

    LaunchedEffect(Unit) {
        refreshStatus { statusState.value = it }
    }

    return remember {
        object : NotificationPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                    UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
                ) { granted, _ ->
                    dispatch_async(dispatch_get_main_queue()) {
                        val status = if (granted) {
                            PermissionStatus.Granted
                        } else {
                            PermissionStatus.Denied(shouldShowRationale = false)
                        }
                        statusState.value = status
                        onResult(status)
                    }
                }
            }
        }
    }
}

private fun refreshStatus(onStatus: (PermissionStatus) -> Unit) {
    UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
        val status = when (settings?.authorizationStatus) {
            UNAuthorizationStatusAuthorized,
            UNAuthorizationStatusProvisional,
            UNAuthorizationStatusEphemeral -> PermissionStatus.Granted
            else -> PermissionStatus.Denied(shouldShowRationale = false)
        }
        dispatch_async(dispatch_get_main_queue()) { onStatus(status) }
    }
}
