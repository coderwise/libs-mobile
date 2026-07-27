package com.coderwise.libs.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

@Composable
actual fun rememberAppSettingsLauncher(): AppSettingsLauncher {
    val context = LocalContext.current
    return remember(context) { AppSettingsLauncher { context.openAppSettings() } }
}

/**
 * NEW_TASK because the context here is only guaranteed to be a Context, not an Activity — and the
 * settings page is its own task either way.
 *
 * Wrapped because the activity this resolves to is not guaranteed to exist: some emulator images
 * and some heavily skinned ROMs ship without it, and a missing settings page is not worth crashing
 * a consumer over. The caller's screen simply keeps showing the refusal.
 */
private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:$packageName".toUri()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nowhere to send the user.
    }
}
