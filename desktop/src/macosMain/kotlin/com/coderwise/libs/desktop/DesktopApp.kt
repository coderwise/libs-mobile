package com.coderwise.libs.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.io.files.Path
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy
import platform.AppKit.NSApplicationDelegateProtocol
import platform.AppKit.NSImage
import platform.AppKit.NSMenu
import platform.AppKit.NSMenuItem
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSProcessInfo
import platform.Foundation.setValue
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
actual fun desktopApp(config: DesktopAppConfig, content: @Composable (darkTheme: Boolean) -> Unit) {
    val dataDir = startDesktopKoin(config)
    val prefsStore = WindowPrefsStore(Path(dataDir, "window.json"))

    nameApp(config.appName)
    val app = NSApplication.sharedApplication()
    val delegate = DesktopAppDelegate(onAbout = { showAbout(config) })
    delegateRef = delegate
    app.delegate = delegate
    app.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)
    loadIcon(config.iconResource)?.let { app.setApplicationIconImage(it) }
    installMainMenu(config.appName, delegate, showAbout = config.about != null)
    trackMainWindow(prefsStore, config.defaultWindowSize, config.unifiedTitleBar)

    Window(config.appName) { content(isSystemInDarkTheme()) }

    app.activateIgnoringOtherApps(true)
    app.run()
}

private fun showAbout(config: DesktopAppConfig) {
    if (config.about == null) return
    Window("About ${config.appName}") {
        AboutWindowContent(config, icon = null, onClose = {})
    }
}

/**
 * An unbundled binary has no Info.plist, so AppKit names the app after the executable (`desktop-macos.kexe`)
 * in the menu bar and Dock. The main bundle's info dictionary is in practice mutable, and AppKit reads the
 * name from it when the application object is created, so this must run before that.
 */
private fun nameApp(appName: String) {
    NSProcessInfo.processInfo.processName = appName
    val info = NSBundle.mainBundle.infoDictionary as? NSMutableDictionary ?: return
    info.setValue(appName, forKey = "CFBundleName")
    info.setValue(appName, forKey = "CFBundleDisplayName")
}

@OptIn(ExperimentalForeignApi::class)
private fun installMainMenu(appName: String, delegate: NSObject, showAbout: Boolean) {
    val appMenu = NSMenu()
    if (showAbout) {
        appMenu.addItem(
            NSMenuItem("About $appName", NSSelectorFromString("showAbout"), "").apply { target = delegate },
        )
        appMenu.addItem(NSMenuItem.separatorItem())
    }
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
