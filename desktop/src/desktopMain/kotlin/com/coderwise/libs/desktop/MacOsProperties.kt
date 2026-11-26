package com.coderwise.libs.desktop

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val isMacOs: Boolean = System.getProperty("os.name").startsWith("Mac")

actual val unifiedTitleBarInset: Dp = if (isMacOs) 78.dp else 0.dp

actual fun prepareMacOs(appName: String) {
    System.setProperty("apple.awt.application.name", appName)
    System.setProperty("apple.awt.application.appearance", "system")
}
