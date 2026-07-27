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

/**
 * Whether this result — the status a request came back with, against [before], the status it was
 * launched from — leaves the user stuck with no way forward inside the app. That is what
 * [rememberAppSettingsLauncher] is for.
 *
 * Neither half is enough alone. A refusal carrying no rationale looks identical whether the user
 * has just this second declined the prompt or decided months ago, and on Android it also looks
 * identical to never having been asked. But a request that changed nothing is one the platform
 * answered from a decision already on file, without showing the user anything — the case where a
 * button appears to do nothing at all.
 */
fun PermissionStatus.isDeadEndAfter(before: PermissionStatus): Boolean =
    this !is PermissionStatus.Granted && this == before && !shouldShowRationale
