package com.coderwise.libs.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareTextLauncher(): ShareTextLauncher {
    val context = LocalContext.current
    return remember(context) {
        object : ShareTextLauncher {
            override fun share(text: String, title: String) {
                context.startShareChooser(text = text, title = title, newTask = false)
            }
        }
    }
}
