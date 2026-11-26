package com.coderwise.libs.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.context.startKoin
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy
import platform.AppKit.NSApplicationDelegateProtocol
import platform.AppKit.NSImage
import platform.AppKit.NSMenu
import platform.AppKit.NSMenuItem
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.darwin.NSObject

internal class DesktopAppDelegate(private val onAbout: () -> Unit) : NSObject(), NSApplicationDelegateProtocol {
    override fun applicationShouldTerminateAfterLastWindowClosed(sender: NSApplication): Boolean = true

    @ObjCAction
    fun showAbout() = onAbout()
}

// NSApplication holds its delegate weakly; keep a strong reference.
private var delegateRef: DesktopAppDelegate? = null

@OptIn(ExperimentalForeignApi::class)
actual fun desktopApp(config: DesktopAppConfig, content: @Composable () -> Unit) {
    val dataDir = config.dataDir ?: Path(NSHomeDirectory(), config.dataDirName)
    SystemFileSystem.createDirectories(dataDir)
    startKoin { modules(config.koinModules(dataDir)) }
    val windowSettings = WindowSettings(Path(dataDir, "window.json"), config.defaultWindowSize)

    NSProcessInfo.processInfo.processName = config.appName
    val app = NSApplication.sharedApplication()
    val delegate = DesktopAppDelegate(onAbout = { showAbout(config) })
    delegateRef = delegate
    app.delegate = delegate
    app.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)
    loadIcon(config.iconResource)?.let { app.setApplicationIconImage(it) }
    installMainMenu(config.appName, delegate)
    trackMainWindow(windowSettings, config.unifiedTitleBar)

    Window(config.appName) { content() }

    app.activateIgnoringOtherApps(true)
    app.run()
}

private fun showAbout(config: DesktopAppConfig) {
    Window("About ${config.appName}") {
        config.theme(isSystemInDarkTheme()) {
            Surface(Modifier.fillMaxSize()) {
                val aboutContent = config.aboutContent
                if (aboutContent != null) {
                    aboutContent(null) {}
                } else {
                    AboutDialogContent(config.appName, config.appVersion, config.about, icon = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun installMainMenu(appName: String, delegate: NSObject) {
    val appMenu = NSMenu()
    appMenu.addItem(
        NSMenuItem("About $appName", NSSelectorFromString("showAbout"), "").apply { target = delegate },
    )
    appMenu.addItem(NSMenuItem.separatorItem())
    appMenu.addItem(NSMenuItem("Quit $appName", NSSelectorFromString("terminate:"), "q"))

    val appItem = NSMenuItem().apply { submenu = appMenu }
    NSApplication.sharedApplication().mainMenu = NSMenu().apply { addItem(appItem) }
}

/** A resource in the app bundle, or else a plain file path; null when neither exists or it is not an image. */
private fun loadIcon(resource: String): NSImage? {
    val name = resource.removePrefix("/")
    val path = NSBundle.mainBundle.pathForResource(name.substringBeforeLast('.'), name.substringAfterLast('.', "").ifEmpty { null })
        ?: name.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
        ?: return null
    return NSImage(contentsOfFile = path).takeIf { it.isValid() }
}
