package com.coderwise.libs.experiment.map.tiles

import androidx.compose.runtime.snapshotFlow
import com.coderwise.libs.experiment.map.MapState
import com.coderwise.libs.experiment.map.TileKey
import com.coderwise.libs.experiment.map.TileWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Fills a [MapState]'s slots, and is the only thing in the app that fetches.
 *
 * The workers run on whatever [scope] they are given, and [T] is whatever [fetch] returns —
 * the bytes off the wire, or a decoded tile if the decoding belongs upstream of the view. Where a
 * tile comes from and what it costs to make, blocking dispatcher and all, is [fetch]'s business;
 * the queue only decides which tile is worth having next, and how many to keep.
 *
 * A real queue rather than a coroutine per tile: the view publishes the window it wants, this
 * replaces its want-list with that window, and a fixed set of workers takes the most useful tile
 * still outstanding — nearest the middle of the screen first. Tiles that leave the window are
 * dropped from the list before anyone starts them, so a fling costs nothing but the tiles you land
 * on. Failures wait [RETRY_AFTER] before they are worth trying again.
 *
 * **What is on screen always wins.** A second, lower list holds the zoom level either side of the
 * window, so that a pinch has something to show the moment it starts. Those are only ever taken
 * when nothing on screen is outstanding, and never with more than half the workers, so a tile the
 * user is looking at never queues behind one they might look at. They are also not started until
 * the window has held still for [SETTLE]: while the map is moving, every worker is on tiles that
 * are already on screen.
 */
class TileQueue<T>(
    private val scope: CoroutineScope,
    private val state: MapState<T>,
    private val workers: Int = 4,
    private val capacity: Int = 256,
    private val fetch: suspend (TileKey) -> T?
) {
    private val lock = Mutex()
    private val wanted = LinkedHashMap<TileKey, Int>() // key -> distance from the centre
    private val soon = LinkedHashMap<TileKey, Int>() // a zoom level away, wanted only when idle
    private val loading = mutableSetOf<TileKey>()
    private val loadingSoon = mutableSetOf<TileKey>()
    private val failedAt = mutableMapOf<TileKey, TimeMark>()
    private val held = LinkedHashSet<TileKey>() // insertion order = eviction order
    private val work = Channel<Unit>(Channel.CONFLATED)
    private var moved: TimeMark = TimeSource.Monotonic.markNow()
    private var lastZ = -1

    /** What the workers are doing, for anyone who wants to show it. Tiles on screen, not ahead. */
    var pending: Int = 0
        private set
    var loaded: Int = 0
        private set

    init {
        scope.launch { snapshotFlow { state.window }.collect { want(it) } }
        repeat(workers) { scope.launch { work() } }
    }

    private suspend fun want(window: TileWindow) {
        val keys = window.keys
        val centreX = (window.x.first + window.x.last) / 2f
        val centreY = (window.y.first + window.y.last) / 2f
        val count = 1 shl window.z

        lock.withLock {
            wanted.clear()
            keys.forEach { key ->
                if (worth(key)) {
                    val dx = abs(key.x - centreX).let { minOf(it, count - it) }
                    val dy = abs(key.y - centreY)
                    wanted[key] = (dx * dx + dy * dy).toInt()
                }
            }
            soon.clear()
            nextDoor(window).forEachIndexed { rank, key -> if (worth(key)) soon[key] = rank }
            pending = wanted.size
            moved = TimeSource.Monotonic.markNow()
            lastZ = window.z
        }
        evict(keys.toSet())

        work.trySend(Unit)
    }

    /**
     * The tiles a zoom is about to want: the parents of the window, which is what a pinch outwards
     * lands on, and the children of its middle, which is what a pinch inwards lands on. In the
     * order the last zoom suggests — whichever way the map was going, it is probably still going.
     *
     * Parents are few and cover the whole screen; children are four per tile, so only the middle
     * of the screen is worth taking, which is the part a zoom keeps.
     */
    private fun nextDoor(window: TileWindow): List<TileKey> {
        if (window.keys.isEmpty()) return emptyList()
        val parents = if (window.z - 1 >= state.zoomRange.first) {
            window.keys.map { TileKey(it.z - 1, it.x shr 1, it.y shr 1) }.distinct()
        } else {
            emptyList()
        }
        val children = if (window.z + 1 <= state.zoomRange.last) {
            val middleX = (window.x.first + window.x.last) / 2
            val middleY = (window.y.first + window.y.last) / 2
            val count = 1 shl (window.z + 1)
            (0..1).flatMap { dy ->
                (0..1).map { dx ->
                    TileKey(window.z + 1, (middleX * 2 + dx).mod(count), middleY * 2 + dy)
                }
            }
        } else {
            emptyList()
        }
        return if (window.z > lastZ) children + parents else parents + children
    }

    private fun worth(key: TileKey) =
        state.slot(key) == null && key !in loading && key !in loadingSoon && !recentlyFailed(key)

    private suspend fun work() {
        while (true) {
            val key = take()
            if (key == null) {
                // Nothing to do, or nothing worth starting yet: SETTLE is the only wait here, and
                // a window change wakes the channel long before it is up.
                if (waiting()) delay(SETTLE / 2) else work.receive()
                continue
            }
            val tile = runCatching { fetch(key) }.getOrNull()
            lock.withLock {
                loading -= key
                loadingSoon -= key
                if (tile == null) failedAt[key] = TimeSource.Monotonic.markNow() else loaded++
                pending = wanted.size
            }
            if (tile != null) {
                state.put(key, tile)
                lock.withLock { held += key }
            }
            work.trySend(Unit)
        }
    }

    /** True when there is work held back only by [SETTLE], and so worth waking up for. */
    private suspend fun waiting() = lock.withLock { wanted.isEmpty() && soon.isNotEmpty() }

    /**
     * Slots are kept well past the window — panning back should not refetch — but not forever.
     * Oldest first, and never anything on screen. [capacity] is worth lowering when [T] is a
     * decoded tile rather than its bytes, because a decoded tile is far bigger.
     */
    private suspend fun evict(visible: Set<TileKey>) = lock.withLock {
        while (held.size > capacity) {
            val oldest = held.firstOrNull { it !in visible } ?: break
            held -= oldest
            state.forget(oldest)
        }
    }

    /**
     * The most useful outstanding tile: nearest the centre of what is on screen, and only then —
     * with workers to spare and the map standing still — one the next zoom will want.
     */
    private suspend fun take(): TileKey? = lock.withLock {
        wanted.minByOrNull { it.value }?.key?.let { best ->
            wanted.remove(best)
            loading += best
            return@withLock best
        }
        if (loadingSoon.size >= maxOf(1, workers / 2)) return@withLock null
        if (moved.elapsedNow() < SETTLE) return@withLock null
        val best = soon.minByOrNull { it.value }?.key ?: return@withLock null
        soon.remove(best)
        loadingSoon += best
        best
    }

    private fun recentlyFailed(key: TileKey): Boolean {
        val at = failedAt[key] ?: return false
        if (at.elapsedNow() < RETRY_AFTER) return true
        failedAt.remove(key)
        return false
    }

    private companion object {
        val RETRY_AFTER = 10.seconds

        /** How long the map has to hold still before a tile nobody is looking at is worth a request. */
        val SETTLE = 250.milliseconds
    }
}
