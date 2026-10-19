package com.coderwise.libs.mapview.cache

import com.coderwise.libs.mapview.TileKey
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CacheTest {

    private val key = TileKey(14, 4824, 6159)
    private val fresh = byteArrayOf(1, 1, 1)
    private val stale = byteArrayOf(2, 2, 2)

    /** A fetch that counts its calls and answers with [answer], or fails with [failure]. */
    private class Network(var answer: ByteArray? = null, var failure: Throwable? = null) {
        var calls = 0
        val fetch: suspend (TileKey) -> ByteArray? = {
            calls++
            failure?.let { throw it }
            answer
        }
    }

    /** A store that can be told to fail. */
    private class Flaky(private val inner: TileStore = MemoryTileStore()) : TileStore {
        var failReads = false
        var failWrites = false
        override suspend fun get(source: String, key: TileKey): ByteArray? {
            if (failReads) error("unreadable")
            return inner.get(source, key)
        }

        override suspend fun put(source: String, key: TileKey, bytes: ByteArray) {
            if (failWrites) error("unwritable")
            inner.put(source, key, bytes)
        }

        override suspend fun remove(source: String, key: TileKey) = inner.remove(source, key)
        override suspend fun clear(source: String?) = inner.clear(source)
    }

    // --- NoCache -------------------------------------------------------------------------------

    @Test
    fun `no cache is the network alone`() = runTest {
        val store = MemoryTileStore().apply { put("s", key, stale) }
        val network = Network(answer = fresh)
        val load = cached(store, "s", CacheMode.NoCache, network.fetch)

        assertContentEquals(fresh, load(key))
        assertContentEquals(stale, store.get("s", key), "no cache wrote to the store")
        assertEquals(1, network.calls)
    }

    // --- CacheFirst ----------------------------------------------------------------------------

    @Test
    fun `cache first fetches what is not held holds it and does not fetch it twice`() = runTest {
        val store = MemoryTileStore()
        val network = Network(answer = fresh)
        val load = cached(store, "s", CacheMode.CacheFirst, network.fetch)

        assertContentEquals(fresh, load(key))
        assertContentEquals(fresh, load(key))
        assertEquals(1, network.calls)
        assertContentEquals(fresh, store.get("s", key))
    }

    @Test
    fun `cache first answers from the store without asking the network`() = runTest {
        val store = MemoryTileStore().apply { put("s", key, stale) }
        val network = Network(answer = fresh)
        assertContentEquals(stale, cached(store, "s", CacheMode.CacheFirst, network.fetch)(key))
        assertEquals(0, network.calls)
    }

    @Test
    fun `a tile the network does not have is not held`() = runTest {
        val store = MemoryTileStore()
        val load = cached(store, "s", CacheMode.CacheFirst, Network(answer = null).fetch)
        assertNull(load(key))
        assertNull(store.get("s", key))
    }

    @Test
    fun `cache first lets a failed fetch be the fetch's own failure`() = runTest {
        val load = cached(MemoryTileStore(), "s", CacheMode.CacheFirst, Network(failure = IllegalStateException("offline")).fetch)
        assertFailsWith<IllegalStateException> { load(key) }
    }

    // --- NetworkFirst --------------------------------------------------------------------------

    @Test
    fun `network first prefers the network and updates what is held`() = runTest {
        val store = MemoryTileStore().apply { put("s", key, stale) }
        val network = Network(answer = fresh)

        assertContentEquals(fresh, cached(store, "s", CacheMode.NetworkFirst, network.fetch)(key))
        assertContentEquals(fresh, store.get("s", key))
    }

    @Test
    fun `network first falls back to what is held when the network fails or has nothing`() = runTest {
        val store = MemoryTileStore().apply { put("s", key, stale) }

        val failing = Network(failure = IllegalStateException("offline"))
        assertContentEquals(stale, cached(store, "s", CacheMode.NetworkFirst, failing.fetch)(key))

        val empty = Network(answer = null)
        assertContentEquals(stale, cached(store, "s", CacheMode.NetworkFirst, empty.fetch)(key))
    }

    @Test
    fun `network first with nothing held and no network is no tile`() = runTest {
        val load = cached(MemoryTileStore(), "s", CacheMode.NetworkFirst, Network(failure = IllegalStateException()).fetch)
        assertNull(load(key))
    }

    @Test
    fun `being cancelled is not a failed fetch to fall back from`() = runTest {
        val store = MemoryTileStore().apply { put("s", key, stale) }
        val load = cached(store, "s", CacheMode.NetworkFirst, Network(failure = CancellationException("gone")).fetch)
        assertFailsWith<CancellationException> { load(key) }
    }

    // --- OfflineOnly ---------------------------------------------------------------------------

    @Test
    fun `offline only never asks the network`() = runTest {
        val store = MemoryTileStore().apply { put("s", key, stale) }
        val network = Network(answer = fresh)
        val load = cached(store, "s", CacheMode.OfflineOnly, network.fetch)

        assertContentEquals(stale, load(key))
        assertNull(load(TileKey(14, 0, 0)))
        assertEquals(0, network.calls)
    }

    // --- the store is never the reason a tile does not load ------------------------------------

    @Test
    fun `a store that cannot be read is a miss`() = runTest {
        val store = Flaky().apply { failReads = true }
        val network = Network(answer = fresh)
        assertContentEquals(fresh, cached(store, "s", CacheMode.CacheFirst, network.fetch)(key))
        assertEquals(1, network.calls)
    }

    @Test
    fun `a store that cannot be written is skipped`() = runTest {
        val store = Flaky().apply { failWrites = true }
        assertContentEquals(fresh, cached(store, "s", CacheMode.CacheFirst, Network(answer = fresh).fetch)(key))
        assertContentEquals(fresh, cached(store, "s", CacheMode.NetworkFirst, Network(answer = fresh).fetch)(key))
    }

    // --- sources -------------------------------------------------------------------------------

    @Test
    fun `two sources over one store do not answer for each other`() = runTest {
        val store = MemoryTileStore()
        val streets = cached(store, "streets", CacheMode.CacheFirst, Network(answer = fresh).fetch)
        val satellite = cached(store, "satellite", CacheMode.CacheFirst, Network(answer = stale).fetch)

        assertContentEquals(fresh, streets(key))
        assertContentEquals(stale, satellite(key))
        assertContentEquals(fresh, streets(key))
    }

    // --- tileUrl -------------------------------------------------------------------------------

    @Test
    fun `a template is filled in`() {
        assertEquals(
            "https://tile.openstreetmap.org/14/4824/6159.png",
            tileUrl("https://tile.openstreetmap.org/{z}/{x}/{y}.png", key)
        )
    }

    @Test
    fun `tms counts rows from the bottom`() {
        // Level 3 has eight rows: row 2 from the top is row 5 from the bottom.
        assertEquals("t/3/1/5", tileUrl("t/{z}/{x}/{y}", TileKey(3, 1, 2), tms = true))
        assertEquals("t/3/1/2", tileUrl("t/{z}/{x}/{y}", TileKey(3, 1, 2), tms = false))
        assertEquals("t/0/0/0", tileUrl("t/{z}/{x}/{y}", TileKey(0, 0, 0), tms = true))
    }

    @Test
    fun `a placeholder used twice is filled twice`() {
        assertEquals("a/3/1/2?z=3", tileUrl("a/{z}/{x}/{y}?z={z}", TileKey(3, 1, 2)))
    }
}
