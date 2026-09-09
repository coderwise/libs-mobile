package com.coderwise.libs.mapview

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

data class TileKey(val z: Int, val x: Int, val y: Int)

/** The tiles a view wants: one zoom level and the span of tiles covering the viewport. */
@Immutable
data class TileWindow(val z: Int, val x: IntRange, val y: IntRange) {

    val keys: List<TileKey>
        get() {
            val count = 1 shl z
            return y.flatMap { ty -> x.map { tx -> TileKey(z, ((tx % count) + count) % count, ty) } }
                .distinct()
        }

    companion object {
        val Empty = TileWindow(0, IntRange.EMPTY, IntRange.EMPTY)
    }
}

/**
 * What the map is made of: one slot per tile, each its own piece of Compose state.
 *
 * The view only reads slots and publishes the [window] it wants; filling them is somebody else's
 * job — a queue, a view model, a test — which is what lets that job prioritise, retry, persist or
 * be driven by hand. [T] is whatever a slot holds: raw bytes in the simple case, or something
 * already decoded.
 *
 * Reading a slot in a composable subscribes to that slot alone, so a tile arriving redraws its own
 * square and nothing else. Reading is also what makes a slot: filling one the view has never asked
 * about only sets the tile aside until it does, because the state a slot is kept in has to be made
 * where it is read. Whoever fills them can ask what is there with [peek], from any thread.
 */
@OptIn(ExperimentalAtomicApi::class)
@Stable
class MapState<T>(
    val zoomRange: IntRange = 0..19
) {
    /** Written by the view as it lays out, read by whoever fills the slots. */
    var window: TileWindow by mutableStateOf(TileWindow.Empty)
        internal set

    /** Clear the window when the state is no longer being shown, to stop the queue. */
    fun clearWindow() {
        window = TileWindow.Empty
    }

    private val slots = AtomicReference(emptyMap<TileKey, MutableState<T?>>())

    /**
     * Content that arrived before anything was drawing its tile.
     *
     * A slot is a piece of Compose state, and one made off the main thread is unreadable to a
     * composition already in flight — which is what a filler running on its own workers would
     * otherwise do to every tile it fetched ahead of the view. So a tile nobody is looking at waits
     * here instead, in a plain map, and the first cell to ask for it takes it.
     */
    private val waiting = AtomicReference(emptyMap<TileKey, T>())

    /** The current content of a slot, or null if nobody has filled it. A tracked read. */
    fun slot(key: TileKey): T? = holder(key).value

    /**
     * What a slot holds without subscribing to it, and without making one — for a filler asking
     * what it still has to fetch, off whatever thread it does that on.
     */
    fun peek(key: TileKey): T? = slots.load()[key]?.value ?: waiting.load()[key]

    /** Fills a slot. Safe to call from any thread. */
    fun put(key: TileKey, content: T?) {
        slots.load()[key]?.let { holder ->
            take(key)
            holder.value = content
            return
        }
        if (content == null) {
            take(key)
            return
        }
        waitingUpdate { it + (key to content) }
        // A cell may have started drawing this tile in between: hand it what it would have missed.
        slots.load()[key]?.let { holder -> take(key)?.let { holder.value = it } }
    }

    /** Which slots hold something — for a filler deciding what to keep. */
    fun filled(): Set<TileKey> =
        slots.load().filterValues { it.value != null }.keys + waiting.load().keys

    fun forget(key: TileKey) {
        update { it - key }
        take(key)
    }

    /**
     * The state a slot's content lives in, made on first use.
     *
     * Copy-on-write rather than a read-then-write, because the view asks for slots while a loader
     * fills them: two threads racing on one key would otherwise end up holding different states,
     * one writing where nobody is reading. The map turns over once per tile, not per frame.
     */
    private fun holder(key: TileKey): MutableState<T?> {
        slots.load()[key]?.let { return it }
        val fresh = mutableStateOf<T?>(null)
        val installed = update { if (key in it) it else it + (key to fresh) }[key]!!
        take(key)?.let { installed.value = it }
        return installed
    }

    /** Removes what is waiting for [key], if anything, and hands it over. */
    private fun take(key: TileKey): T? {
        while (true) {
            val current = waiting.load()
            val content = current[key] ?: return null
            if (waiting.compareAndSet(current, current - key)) return content
        }
    }

    private inline fun waitingUpdate(edit: (Map<TileKey, T>) -> Map<TileKey, T>) {
        while (true) {
            val current = waiting.load()
            if (waiting.compareAndSet(current, edit(current))) return
        }
    }

    private inline fun update(edit: (Map<TileKey, MutableState<T?>>) -> Map<TileKey, MutableState<T?>>):
        Map<TileKey, MutableState<T?>> {
        while (true) {
            val current = slots.load()
            val next = edit(current)
            if (slots.compareAndSet(current, next)) return next
        }
    }
}
