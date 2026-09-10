package com.coderwise.libs.mapview.tiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.coderwise.libs.mapview.MapState
import com.coderwise.libs.mapview.TileKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation

/**
 * Slots for one source, and the queue that fills them from [load], running on [scope].
 *
 * There is nothing for a layer object to wrap: the slots are the cache — they hold whatever [load]
 * returns, so a tile is made once on the way in — and the queue is the only thing that fetches. It
 * keeps [capacity] of them, evicts the oldest off-screen tile, and decides what to fetch next
 * itself, nearest the middle of the screen first, reaching for the neighbouring zoom levels only
 * once the map has settled. So a caller needs no cache, no fallback resolver and no prefetching of
 * its own.
 *
 * Nor is there any cropping. A cell with no tile of its own draws the nearest filled ancestor
 * magnified — [shown] gives the tile and the fraction of it to draw — and a source that stops
 * short of [zoomRange]'s end simply lays its deepest level out bigger: over-zoom is the grid's
 * doing, not an image operation.
 *
 * The tiles live as long as [scope] does. Hand it a view model's, and they survive the rotation
 * that would otherwise throw away several screens of them and refetch the one on screen.
 */
fun <T> tileState(
    scope: CoroutineScope,
    zoomRange: IntRange = DEFAULT_ZOOM_RANGE,
    capacity: Int = DEFAULT_CAPACITY,
    load: suspend (TileKey) -> T?
): MapState<T> = MapState<T>(zoomRange = zoomRange).also { state ->
    TileQueue(scope, state, capacity = capacity, fetch = load)
}

/**
 * The same, for as long as it is drawn: the queue runs on the composition's own coroutine, so
 * leaving takes the workers and the tiles with it. Changing [key] — the tile source, typically —
 * starts fresh slots, so nothing of the old source is left on screen or in memory.
 *
 * For a screen whose tiles are worth keeping when it goes away, hold them in a view model with
 * [tileState] instead.
 */
@Composable
fun <T> rememberTileState(
    key: Any? = Unit,
    zoomRange: IntRange = DEFAULT_ZOOM_RANGE,
    capacity: Int = DEFAULT_CAPACITY,
    load: suspend (TileKey) -> T?
): MapState<T> {
    // Read when a tile is fetched, not when the slots are made: a screen that hands a fresh lambda
    // down on every recomposition would otherwise start again with it.
    val loader = rememberUpdatedState(load)
    val state = remember(key, zoomRange, capacity) { MapState<T>(zoomRange = zoomRange) }
    LaunchedEffect(state) {
        TileQueue(this, state, capacity = capacity) { loader.value(it) }
        awaitCancellation()
    }
    return state
}

/** Every level the grid lays out — a source that stops earlier says so with its own range. */
val DEFAULT_ZOOM_RANGE = 0..19

/** Tiles held before the oldest off-screen one is evicted: several screens of panning back. */
const val DEFAULT_CAPACITY = 96
