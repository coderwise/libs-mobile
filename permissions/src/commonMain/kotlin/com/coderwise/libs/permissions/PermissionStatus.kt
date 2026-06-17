package com.coderwise.libs.permissions

import androidx.compose.runtime.Stable

@Stable
sealed interface PermissionStatus {
    data object Granted : PermissionStatus
    data class Denied(val shouldShowRationale: Boolean) : PermissionStatus
}

val PermissionStatus.isGranted: Boolean
    get() = this is PermissionStatus.Granted

val PermissionStatus.shouldShowRationale: Boolean
    get() = (this as? PermissionStatus.Denied)?.shouldShowRationale == true
