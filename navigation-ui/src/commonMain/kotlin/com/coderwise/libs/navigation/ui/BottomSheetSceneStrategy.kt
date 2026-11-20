package com.coderwise.libs.navigation.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Draws one entry as a bottom sheet. Gets the sheet's request, which carries the entry's metadata
 * and what to call on dismiss, and the entry's content to put inside the sheet.
 *
 * Each app brings its own, to give the sheet its own shape, header and insets. See
 * [DefaultSheetContainer] for the smallest one.
 */
typealias SheetContainer = @Composable (request: SheetRequest, content: @Composable () -> Unit) -> Unit

/** What a [SheetContainer] gets for the entry it draws. */
@Stable
class SheetRequest internal constructor(
    /** The entry's metadata, for keys of the app's own, such as a sheet title. */
    val metadata: Map<String, Any>,
    /** Call when the user dismisses the sheet: a swipe down, a tap on the scrim, a close button. */
    val onDismiss: () -> Unit,
) {
    internal var hide: suspend () -> Unit = {}

    /**
     * Sets how the sheet animates away when its entry leaves the back stack some other way — a
     * back press, or a view model navigating — so that it slides out rather than vanishing.
     */
    @Composable
    fun HideWith(hide: suspend () -> Unit) {
        val current by rememberUpdatedState(hide)
        DisposableEffect(this) {
            this@SheetRequest.hide = { current() }
            onDispose { this@SheetRequest.hide = {} }
        }
    }
}

/** A plain Material 3 modal bottom sheet, fully expanded, with no header of its own. */
@OptIn(ExperimentalMaterial3Api::class)
val DefaultSheetContainer: SheetContainer = { request, content ->
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    request.HideWith { sheetState.hide() }
    ModalBottomSheet(onDismissRequest = request.onDismiss, sheetState = sheetState) {
        content()
    }
}

/**
 * Shows an entry marked with [bottomSheet] as a sheet over the entries beneath it, which stay on
 * screen. [container] draws the sheet.
 *
 * Put it before any strategy that is not an overlay.
 */
class BottomSheetSceneStrategy<T : Any>(
    private val container: SheetContainer = DefaultSheetContainer,
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? =
        sceneFor(entries, onBack)

    internal fun sceneFor(entries: List<NavEntry<T>>, onBack: () -> Unit): Scene<T>? {
        val entry = entries.lastOrNull() ?: return null
        if (BottomSheetKey !in entry.metadata) return null
        val beneath = entries.dropLast(1)
        return BottomSheetScene(
            key = entry.contentKey,
            entry = entry,
            previousEntries = beneath,
            container = container,
            onDismiss = entry.metadata[OnDismissKey] ?: onBack,
        )
    }

    companion object {
        /**
         * Marks an entry to be shown as a bottom sheet. [onDismiss] replaces the default, which pops
         * the entry. Add metadata of the app's own with `+`, for its [SheetContainer] to read.
         */
        fun bottomSheet(onDismiss: (() -> Unit)? = null): Map<String, Any> = metadata {
            put(BottomSheetKey, Unit)
            if (onDismiss != null) put(OnDismissKey, onDismiss)
        }

        private object BottomSheetKey : NavMetadataKey<Unit>
        private object OnDismissKey : NavMetadataKey<() -> Unit>
    }
}

internal class BottomSheetScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val container: SheetContainer,
    onDismiss: () -> Unit,
) : OverlayScene<T> {

    internal val request = SheetRequest(entry.metadata, onDismiss)

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val overlaidEntries: List<NavEntry<T>> = previousEntries

    override val content: @Composable () -> Unit = {
        container(request) { entry.Content() }
    }

    override suspend fun onRemove() {
        request.hide()
    }

    // A scene that compares equal is kept rather than replaced, so the sheet isn't redrawn from
    // scratch when the back stack changes beneath it.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BottomSheetScene<*>) return false
        return key == other.key &&
            entry == other.entry &&
            previousEntries == other.previousEntries
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + entry.hashCode()
        result = 31 * result + previousEntries.hashCode()
        return result
    }
}
