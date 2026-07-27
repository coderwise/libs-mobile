package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// The browser's site permissions are reachable only from its own chrome — a page cannot open them.
@Composable
actual fun rememberAppSettingsLauncher(): AppSettingsLauncher = remember { AppSettingsLauncher { } }
