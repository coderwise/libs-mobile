package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Desktop grants everything this library models, so there is no refusal to send the user away from.
@Composable
actual fun rememberAppSettingsLauncher(): AppSettingsLauncher = remember { AppSettingsLauncher { } }
