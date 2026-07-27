package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Sends the user to this app's own page in the OS settings, where the permission toggles live.
 *
 * This belongs next to the permission states because it is where a refusal that no longer prompts
 * ends up: Android's "don't ask again" and every iOS denial answer a request without showing the
 * user anything, so a screen holding a [PermissionStatus.Denied] with no rationale left to show has
 * nowhere else to send them. Whether a refusal has reached that point is
 * [PermissionStatus.shouldShowRationale] on the state that reported it.
 *
 * A grant made over in the settings app arrives back the way any other one does — as a change to
 * the permission state, which re-reads on resume.
 *
 * On desktop and JS this does nothing: there is no per-app settings page to open.
 */
@Stable
fun interface AppSettingsLauncher {
    fun launch()
}

@Composable
expect fun rememberAppSettingsLauncher(): AppSettingsLauncher
