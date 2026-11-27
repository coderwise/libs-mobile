package com.coderwise.libs.desktop

import androidx.compose.ui.unit.DpSize
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AppKit.NSScreen
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowDidMoveNotification
import platform.AppKit.NSWindowDidResizeNotification
import platform.AppKit.NSWindowTitleHidden
import platform.AppKit.NSWindowStyleMaskFullSizeContentView
import platform.AppKit.NSWindowWillCloseNotification
import platform.AppKit.NSWindowDidBecomeMainNotification
import platform.Foundation.NSMakeRect
import platform.Foundation.NSMakeSize
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Applies the remembered frame to the first window that becomes main (the app's window, not a later
 * About window) and saves it on resize, move and close. Notification-driven; no polling.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun trackMainWindow(settings: WindowPrefsStore, defaultSize: DpSize, minSize: DpSize?, unifiedTitleBar: Boolean) {
    var main: NSWindow? = null
    // Notifications are delivered inline, so the style change below reports a resize before the saved
    // frame is read; saving then would overwrite the file with Compose's initial size.
    var restoring = false
    val center = NSNotificationCenter.defaultCenter
    val queue = NSOperationQueue.mainQueue

    center.addObserverForName(NSWindowDidBecomeMainNotification, null, queue) { note ->
        val window = note?.`object` as? NSWindow ?: return@addObserverForName
        if (main != null) return@addObserverForName
        main = window
        restoring = true
        minSize?.let { window.contentMinSize = NSMakeSize(it.width.value.toDouble(), it.height.value.toDouble()) }
        if (unifiedTitleBar) {
            window.titlebarAppearsTransparent = true
            window.titleVisibility = NSWindowTitleHidden
            window.styleMask = window.styleMask or NSWindowStyleMaskFullSizeContentView
        }
        restore(window, settings, defaultSize)
        restoring = false
        // Compose lays its content out for the plain title bar and only re-reads the window size when it
        // changes, so without a size change the unified title bar shows up only after the first resize.
        if (unifiedTitleBar) dispatch_async(dispatch_get_main_queue()) { nudgeLayout(window) }
    }

    for (name in listOf(NSWindowDidResizeNotification, NSWindowDidMoveNotification, NSWindowWillCloseNotification)) {
        center.addObserverForName(name, null, queue) { note ->
            val window = note?.`object` as? NSWindow ?: return@addObserverForName
            if (window !== main || restoring) return@addObserverForName
            save(window, settings)
        }
    }
}

/** Resizes the window by a point and back, which makes Compose lay out again for the current title bar style. */
@OptIn(ExperimentalForeignApi::class)
private fun nudgeLayout(window: NSWindow) {
    val frame = window.frame
    frame.useContents {
        window.setFrame(NSMakeRect(origin.x, origin.y, size.width, size.height + 1.0), display = true)
        window.setFrame(NSMakeRect(origin.x, origin.y, size.width, size.height), display = true)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun restore(window: NSWindow, settings: WindowPrefsStore, defaultSize: DpSize) {
    val prefs = settings.load()
    if (prefs.width <= 0f || prefs.height <= 0f) {
        window.setContentSize(NSMakeSize(defaultSize.width.value.toDouble(), defaultSize.height.value.toDouble()))
        window.center()
        return
    }
    val x = prefs.x.toDouble()
    val y = prefs.y.toDouble()
    val w = prefs.width.toDouble()
    val h = prefs.height.toDouble()
    if (prefs.hasPosition && isOnScreen(x, y, w, h)) {
        window.setFrame(NSMakeRect(x, y, w, h), display = true)
    } else {
        window.setContentSize(NSMakeSize(w, h))
        window.center()
    }
    if (prefs.maximized && !window.isZoomed()) window.zoom(null)
}

@OptIn(ExperimentalForeignApi::class)
private fun save(window: NSWindow, settings: WindowPrefsStore) {
    val old = settings.prefs
    if (window.isZoomed()) {
        // While zoomed the frame is the zoomed bounds; keep the last normal ones.
        settings.save(old.copy(maximized = true))
        return
    }
    window.frame.useContents {
        settings.save(
            old.copy(
                maximized = false,
                width = size.width.toFloat(),
                height = size.height.toFloat(),
                hasPosition = true,
                x = origin.x.toFloat(),
                y = origin.y.toFloat(),
            ),
        )
    }
}

/** Ignores a saved frame that is off every screen (e.g. a monitor was unplugged). */
@OptIn(ExperimentalForeignApi::class)
private fun isOnScreen(x: Double, y: Double, w: Double, h: Double): Boolean =
    NSScreen.screens.filterIsInstance<NSScreen>().any { screen ->
        screen.frame.useContents {
            x < origin.x + size.width && x + w > origin.x && y < origin.y + size.height && y + h > origin.y
        }
    }
