package com.coderwise.libs.navigation.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Draws an entry marked with [overlay] on top of the entries beneath it, which stay on screen.
 * Unlike [BottomSheetSceneStrategy], the entry draws its own window (a dialog, or a sheet with
 * behaviour of its own) and pops itself when it is dismissed.
 *
 * Put it before any strategy that is not an overlay.
 */
class OverlaySceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? =
        sceneFor(entries)

    internal fun sceneFor(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.lastOrNull() ?: return null
        if (OverlayKey !in entry.metadata) return null
        return EntryOverlayScene(
            key = entry.contentKey,
            entry = entry,
            previousEntries = entries.dropLast(1),
        )
    }

    companion object {
        /** Marks an entry to be drawn over the one beneath it. */
        fun overlay(): Map<String, Any> = metadata { put(OverlayKey, Unit) }

        private object OverlayKey : NavMetadataKey<Unit>
    }
}

internal data class EntryOverlayScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
) : OverlayScene<T> {
    override val overlaidEntries: List<NavEntry<T>> = previousEntries
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = { entry.Content() }
}
