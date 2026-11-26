package com.coderwise.libs.desktop

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val isMacOs: Boolean = System.getProperty("os.name").startsWith("Mac")

/**
 * The start inset an app's top bar must leave free for the macOS window buttons when
 * [DesktopAppConfig.unifiedTitleBar] is on; zero on other platforms, where the title bar is the
 * system's own.
 */
val unifiedTitleBarInset: Dp = if (isMacOs) 78.dp else 0.dp

/**
 * Sets the macOS properties AWT reads once, when its toolkit starts: the menu-bar name (otherwise the
 * main class, `MainKt`) and the system appearance (otherwise the title bar stays light in dark mode).
 *
 * [desktopApp] calls this, but call it yourself as the first line of `main` too: under Compose Hot
 * Reload the toolkit can be up before [desktopApp] runs. It touches nothing but system properties.
 */
fun prepareMacOs(appName: String) {
    System.setProperty("apple.awt.application.name", appName)
    System.setProperty("apple.awt.application.appearance", "system")
}
