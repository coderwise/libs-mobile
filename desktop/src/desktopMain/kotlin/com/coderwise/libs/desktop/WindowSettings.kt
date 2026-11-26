package com.coderwise.libs.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/** Maps the remembered [WindowPrefs] to and from the main window's Compose `WindowState`. */
internal class WindowSettings(private val store: WindowPrefsStore, private val defaultSize: DpSize) {
    /** Called once at startup, before the window exists. */
    fun load(): WindowState {
        val prefs = store.load()
        val state = WindowState(size = defaultSize)
        if (prefs.width > 0f && prefs.height > 0f) state.size = DpSize(prefs.width.dp, prefs.height.dp)
        if (prefs.hasPosition) {
            // Ignore a saved position that is off every screen (e.g. a monitor was unplugged).
            val bounds = Rectangle(
                prefs.x.toInt(),
                prefs.y.toInt(),
                state.size.width.value.toInt(),
                state.size.height.value.toInt(),
            )
            if (isOnScreen(bounds)) state.position = WindowPosition(prefs.x.dp, prefs.y.dp)
        }
        if (prefs.maximized) state.placement = WindowPlacement.Maximized
        return state
    }

    fun save(state: WindowState) {
        val old = store.prefs
        val maximized = state.placement == WindowPlacement.Maximized
        // While maximized the size and position are the maximized bounds; keep the last normal ones.
        val position = state.position
        val updated = if (maximized) {
            old.copy(maximized = true)
        } else {
            old.copy(
                maximized = false,
                width = state.size.width.value,
                height = state.size.height.value,
                hasPosition = position.isSpecified || old.hasPosition,
                x = if (position.isSpecified) position.x.value else old.x,
                y = if (position.isSpecified) position.y.value else old.y,
            )
        }
        store.save(updated)
    }

    private fun isOnScreen(bounds: Rectangle): Boolean =
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any {
            it.defaultConfiguration.bounds.intersects(bounds)
        }
}
