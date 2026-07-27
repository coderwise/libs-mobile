package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberAppSettingsLauncher(): AppSettingsLauncher =
    remember { AppSettingsLauncher { openIosAppSettings() } }
