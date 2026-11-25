package com.coderwise.libs.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * What an app tells the desktop bootstrap about itself. Everything else (macOS menu-bar name and
 * appearance, Koin start-up, data directory, dock icon, About window, remembered window size
 * and position) is done the same way for every app by [desktopApp].
 */
class DesktopAppConfig(
    /** Window title, macOS menu-bar name and About title. */
    val appName: String,
    /** Folder under the user's home for this app's files, e.g. `.roomsOn`. */
    val dataDirName: String,
    /** Display version, e.g. from the app's generated BuildInfo. */
    val appVersion: String,
    val about: AboutInfo,
    /**
     * The app's Koin modules. Gets the data directory so it can register what needs one, such as
     * a database or settings folder.
     */
    val koinModules: (dataDir: File) -> List<Module>,
    /** The app's theme, used for the About window. */
    val theme: @Composable (darkTheme: Boolean, content: @Composable () -> Unit) -> Unit,
    /** Classpath location of the app icon (dock, window, About). */
    val iconResource: String = "/icon.png",
    /** Size of the first window, before a size has been remembered. */
    val defaultWindowSize: DpSize = DpSize(1100.dp, 720.dp),
)

/**
 * The whole desktop `main`. Depends on no other Coderwise library; the app wires those in through
 * [DesktopAppConfig.koinModules]:
 *
 * ```kotlin
 * fun main() {
 *     prepareMacOs("RoomsOn") // first line: see [prepareMacOs]
 *     desktopApp(DesktopAppConfig(appName = "RoomsOn", ...)) { AppNavigation() }
 * }
 * ```
 */
fun desktopApp(config: DesktopAppConfig, content: @Composable () -> Unit) {
    prepareMacOs(config.appName)
    val dataDir = File(System.getProperty("user.home"), config.dataDirName)
    startKoin { modules(config.koinModules(dataDir)) }
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
            DialogWindow(onCloseRequest = { showAbout = false }, title = "About ${config.appName}") {
                config.theme(isDark) {
                    Surface {
                        AboutDialogContent(config.appName, config.appVersion, config.about, icon)
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
            content()
        }
    }
}
