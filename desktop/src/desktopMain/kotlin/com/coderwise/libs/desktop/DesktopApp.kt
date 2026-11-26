package com.coderwise.libs.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import kotlinx.io.files.Path
import org.koin.core.context.startKoin
import java.io.File

actual fun desktopApp(config: DesktopAppConfig, content: @Composable () -> Unit) {
    prepareMacOs(config.appName)
    val dataDirPath = config.dataDir ?: Path(System.getProperty("user.home"), config.dataDirName)
    val dataDir = File(dataDirPath.toString())
    startKoin { modules(config.koinModules(dataDirPath)) }
    val windowSettings = WindowSettings(File(dataDir, "window.json"), config.defaultWindowSize)
    val iconImage = loadIcon(config.iconResource)
    val icon = iconImage?.toPainter()

    application {
        var showAbout by remember { mutableStateOf(false) }

        remember {
            setDockIcon(iconImage)
            setAboutHandler { showAbout = true }
        }

        val windowState = remember { windowSettings.load() }
        LaunchedEffect(windowState) {
            snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
                .collect { windowSettings.save(windowState) }
        }

        if (showAbout) {
            val isDark = isSystemInDarkTheme()
            val aboutContent = config.aboutContent
            DialogWindow(
                onCloseRequest = { showAbout = false },
                title = "About ${config.appName}",
                state = rememberDialogState(size = config.aboutWindowSize),
                resizable = aboutContent == null,
            ) {
                config.theme(isDark) {
                    Surface(Modifier.fillMaxSize()) {
                        if (aboutContent != null) {
                            aboutContent(icon) { showAbout = false }
                        } else {
                            AboutDialogContent(config.appName, config.appVersion, config.about, icon)
                        }
                    }
                }
            }
        }

        Window(
            onCloseRequest = {
                windowSettings.save(windowState)
                exitApplication()
            },
            state = windowState,
            title = config.appName,
            icon = icon,
        ) {
            if (config.unifiedTitleBar && isMacOs) {
                SideEffect {
                    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                }
            }
            content()
        }
    }
}
