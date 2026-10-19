package com.coderwise.libs.mapview.cache

import com.coderwise.libs.mapview.TileKey
import kotlin.coroutines.cancellation.CancellationException

/** How a cache and the network share the work of getting a tile. */
enum class CacheMode {
    /** The network alone. Nothing is read from the store and nothing is written to it. */
    NoCache,

    /** What is held, and the network for what is not — which is then held. The usual choice. */
    CacheFirst,

    /** The network, for tiles that should be fresh; what is held only when the network fails. */
    NetworkFirst,

    /** What is held and nothing else, for a map that must not use the network at all. */
    OfflineOnly
}

/**
 * Wraps [fetch] so that [store] answers when it can: the function a tile queue is given, with a
 * cache in front of it. The queue never knows.
 *
 * ```
 * val bytes = cached(store, source = "streets", mode = CacheMode.CacheFirst) { key ->
 *     http.get(tileUrl(template, key)).readRawBytes()
 * }
 * val ground = rememberTileState(key = "streets") { key -> bytes(key)?.let { decodeVectorTile(key.z, it) } }
 * ```
 *
 * What is cached is the bytes, before they are decoded, so one cache serves every way a tile might
 * be turned into something to draw — and a style change redraws without fetching again.
 *
 * A cache is never the reason a tile does not load: a store that cannot be read is a miss, and one
 * that cannot be written is skipped. And a failed fetch is [fetch]'s own failure — except in
 * [CacheMode.NetworkFirst], where it is exactly what the cache is there for.
 *
 * [source] names where the tiles come from, and has to be different for every source sharing a
 * [store]: it is what stops a street map's tile 14/4824/6159 being answered with a satellite one.
 */
fun cached(
    store: TileStore,
    source: String,
    mode: CacheMode = CacheMode.CacheFirst,
    fetch: suspend (TileKey) -> ByteArray?
): suspend (TileKey) -> ByteArray? = { key ->
    when (mode) {
        CacheMode.NoCache -> fetch(key)
        CacheMode.CacheFirst -> store.recall(source, key) ?: fetch(key)?.also { store.keep(source, key, it) }
        CacheMode.NetworkFirst ->
            attempt { fetch(key) }?.also { store.keep(source, key, it) } ?: store.recall(source, key)

        CacheMode.OfflineOnly -> store.recall(source, key)
    }
}

private suspend fun TileStore.recall(source: String, key: TileKey): ByteArray? = attempt { get(source, key) }

private suspend fun TileStore.keep(source: String, key: TileKey, bytes: ByteArray) {
    attempt { put(source, key, bytes) }
}

/** The result of [block], or null if it failed — anything but being cancelled. */
private suspend fun <T> attempt(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/**
 * The URL of [key] for a `{z}/{x}/{y}` [template] — `https://tile.openstreetmap.org/{z}/{x}/{y}.png`.
 *
 * Rows count from the top, as they do in the XYZ scheme everything here uses. Some servers count
 * from the bottom instead — TMS — and want [tms] to flip the row.
 */
fun tileUrl(template: String, key: TileKey, tms: Boolean = false): String {
    val row = if (tms) (1 shl key.z) - 1 - key.y else key.y
    return template
        .replace("{z}", key.z.toString())
        .replace("{x}", key.x.toString())
        .replace("{y}", row.toString())
}
