package com.coderwise.libs.mapcore

import kotlin.jvm.JvmInline

/**
 * Identity of a single XYZ map tile — its `(zoom, x, y)` address, **not** its content (that's a
 * decoded bitmap / vector tile). Shared across the whole stack: the rendering engine, the tile
 * renderer, and the domain/data layer all speak this one type.
 *
 * The triple is packed into a single [key] `Long` (zoom in the high 8 bits, x and y in 28 bits
 * each). That packed form is the canonical identity: it is the SQLite tile-cache primary key, the
 * in-memory cache key, and a Compose `key()` — so persisting and caching never need to unpack.
 *
 * An `@JvmInline value class`, so at runtime it erases to a bare `Long` (no wrapper allocation)
 * while staying type-safe. It re-boxes only in generic positions (`List<TileId>`, `Set<TileId>`,
 * Compose `key`), where equality is structural on [key]; the hot caches key on the raw `Long`.
 */
@JvmInline
value class TileId(val key: Long) {
    val zoom: Int get() = (key shr 56 and 0xFFL).toInt()
    val x: Int get() = (key shr 28 and 0xFFFFFFFL).toInt()
    val y: Int get() = (key and 0xFFFFFFFL).toInt()

    constructor(zoom: Int, x: Int, y: Int) : this(computeKey(zoom, x, y))

    companion object {
        fun computeKey(zoom: Int, x: Int, y: Int): Long {
            return (zoom.toLong() and 0xFFL shl 56) or
                    (x.toLong() and 0xFFFFFFFL shl 28) or
                    (y.toLong() and 0xFFFFFFFL)
        }
    }
}
