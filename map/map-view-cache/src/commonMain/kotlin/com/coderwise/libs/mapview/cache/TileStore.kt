package com.coderwise.libs.mapview.cache

import com.coderwise.libs.mapview.TileKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where tile bytes are kept between fetches.
 *
 * A tile is addressed by the source it came from and its [TileKey], and by nothing derived from
 * them: two sources, or two tiles, can never be mistaken for one another because there is no hash
 * for them to collide in.
 *
 * Bytes handed to [put] and returned by [get] are shared, not copied — treat them as read-only.
 */
interface TileStore {
    /** The bytes held for [key] of [source], or null. Counts as a use, for stores that evict by it. */
    suspend fun get(source: String, key: TileKey): ByteArray?

    suspend fun put(source: String, key: TileKey, bytes: ByteArray)

    suspend fun remove(source: String, key: TileKey)

    /** Forgets everything held for [source], or everything when it is null. */
    suspend fun clear(source: String? = null)
}

/**
 * Tiles held in memory, up to [maxBytes], the least recently used going first.
 *
 * For the browser, whose HTTP cache already does a disk's job, and for tests. Gone with the
 * process.
 */
class MemoryTileStore(private val maxBytes: Long = 64L * 1024 * 1024) : TileStore {

    private class Id(val source: String, val key: TileKey) {
        override fun equals(other: Any?) = other is Id && other.source == source && other.key == key
        override fun hashCode() = 31 * source.hashCode() + key.hashCode()
    }

    private val lock = Mutex()
    private val tiles = LinkedHashMap<Id, ByteArray>() // least recently used first
    private var held = 0L

    /** How many bytes are held. */
    suspend fun size(): Long = lock.withLock { held }

    override suspend fun get(source: String, key: TileKey): ByteArray? = lock.withLock {
        val id = Id(source, key)
        // Taken out and put back, which is what moves it to the recent end.
        tiles.remove(id)?.also { tiles[id] = it }
    }

    override suspend fun put(source: String, key: TileKey, bytes: ByteArray) {
        lock.withLock {
            val id = Id(source, key)
            tiles.remove(id)?.let { held -= it.size }
            if (bytes.size > maxBytes) return // bigger than the whole store: not worth emptying it for
            tiles[id] = bytes
            held += bytes.size
            val oldest = tiles.entries.iterator()
            while (held > maxBytes && oldest.hasNext()) {
                held -= oldest.next().value.size
                oldest.remove()
            }
        }
    }

    override suspend fun remove(source: String, key: TileKey) {
        lock.withLock { tiles.remove(Id(source, key))?.let { held -= it.size } }
    }

    override suspend fun clear(source: String?) {
        lock.withLock {
            if (source == null) {
                tiles.clear()
                held = 0
            } else {
                val all = tiles.entries.iterator()
                while (all.hasNext()) {
                    val entry = all.next()
                    if (entry.key.source == source) {
                        held -= entry.value.size
                        all.remove()
                    }
                }
            }
        }
    }
}
