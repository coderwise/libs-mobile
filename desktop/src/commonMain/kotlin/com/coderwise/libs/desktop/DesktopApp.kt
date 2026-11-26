package com.coderwise.libs.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.io.files.Path
import org.koin.core.module.Module

/**
 * What an app tells the desktop bootstrap about itself. Everything else (macOS menu-bar name and
 * appearance, Koin start-up, data directory, dock icon, About window, remembered window size
 * and position) is done the same way for every app by [desktopApp].
 */
class DesktopAppConfig(
    /** Window title, menu-bar name and About title. */
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
    val koinModules: (dataDir: Path) -> List<Module>,
    /** The app's theme, used for the About window. */
    val theme: @Composable (darkTheme: Boolean, content: @Composable () -> Unit) -> Unit,
    /**
     * The app icon (dock, window, About). On the JVM a classpath location; on native macOS a resource
     * in the app bundle, or else a file path.
     */
    val iconResource: String = "/icon.png",
    /** Size of the first window, before a size has been remembered. */
    val defaultWindowSize: DpSize = DpSize(1100.dp, 720.dp),
    /**
     * Where the app keeps its files, for an app that is not content with `~/<dataDirName>` (a per-OS
     * application-support folder, say). Overrides [dataDirName].
     */
    val dataDir: Path? = null,
    /**
     * The About window's content, for an app whose About says more than [about] can: it replaces the
     * default content but still gets the lib's window, [theme] and surface. Gets the app icon (null on
     * native macOS) and a way to close the window (a no-op on native macOS, where the window's own
     * close button does it).
     */
    val aboutContent: (@Composable (icon: Painter?, onClose: () -> Unit) -> Unit)? = null,
    /** Size of the About window; with [aboutContent] it is also fixed, not resizable. Ignored on native macOS. */
    val aboutWindowSize: DpSize = DpSize(400.dp, 300.dp),
    /**
     * Draws the app's content under a transparent macOS title bar, leaving only the window buttons
     * (a unified title bar). The app's top bar must then leave [unifiedTitleBarInset] free at its
     * start. No effect on other platforms.
     */
    val unifiedTitleBar: Boolean = false,
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
expect fun desktopApp(config: DesktopAppConfig, content: @Composable () -> Unit)

/**
 * The start inset an app's top bar must leave free for the macOS window buttons when
 * [DesktopAppConfig.unifiedTitleBar] is on; zero on other platforms, where the title bar is the
 * system's own.
 */
expect val unifiedTitleBarInset: Dp

/**
 * JVM: sets the macOS properties AWT reads once, when its toolkit starts (menu-bar name, system
 * appearance); call it as the first line of `main`, since under Compose Hot Reload the toolkit can be
 * up before [desktopApp] runs. Native macOS: does nothing, there is no AWT.
 */
expect fun prepareMacOs(appName: String)
