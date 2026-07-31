package com.coderwise.libs.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberShareTextLauncher(): ShareTextLauncher = remember {
    object : ShareTextLauncher {
        // iOS has no chooser title; the activity controller labels itself.
        override fun share(text: String, title: String) = presentShareSheet(text)
    }
}
