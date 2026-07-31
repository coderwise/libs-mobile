package com.coderwise.libs.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberShareTextLauncher(): ShareTextLauncher = remember {
    object : ShareTextLauncher {
        override fun share(text: String, title: String) = copyToClipboard(text)
    }
}
