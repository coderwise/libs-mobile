package com.coderwise.libs.mapview.cache

import com.coderwise.libs.mapview.TileKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryTileStoreTest {

    private val key = TileKey(14, 4824, 6159)
    private fun bytes(size: Int, fill: Int = 1) = ByteArray(size) { fill.toByte() }

    @Test
    fun `what is put is got and what is not is not`() = runTest {
        val store = MemoryTileStore()
        assertNull(store.get("streets", key))
        store.put("streets", key, bytes(10, fill = 7))
        assertContentEquals(bytes(10, fill = 7), store.get("streets", key))
        assertNull(store.get("streets", TileKey(14, 4824, 6160)))
    }

    @Test
    fun `two sources do not answer for each other`() = runTest {
        val store = MemoryTileStore()
        store.put("streets", key, bytes(4, fill = 1))
        store.put("satellite", key, bytes(4, fill = 2))
        assertContentEquals(bytes(4, fill = 1), store.get("streets", key))
        assertContentEquals(bytes(4, fill = 2), store.get("satellite", key))
    }

    @Test
    fun `putting again replaces and the size follows`() = runTest {
        val store = MemoryTileStore()
        store.put("s", key, bytes(100))
        store.put("s", key, bytes(30, fill = 9))
        assertContentEquals(bytes(30, fill = 9), store.get("s", key))
        assertEquals(30L, store.size())
    }

    @Test
    fun `the least recently used goes first`() = runTest {
        val store = MemoryTileStore(maxBytes = 30)
        val a = TileKey(1, 0, 0)
        val b = TileKey(1, 1, 0)
        val c = TileKey(1, 0, 1)
        val d = TileKey(1, 1, 1)
        store.put("s", a, bytes(10))
        store.put("s", b, bytes(10))
        store.put("s", c, bytes(10))
        store.get("s", a) // a is the newest now, b the oldest
        store.put("s", d, bytes(10))

        assertNull(store.get("s", b))
        assertEquals(10, store.get("s", a)?.size)
        assertEquals(10, store.get("s", c)?.size)
        assertEquals(10, store.get("s", d)?.size)
        assertEquals(30L, store.size())
    }

    @Test
    fun `a tile bigger than the whole store is not worth emptying it for`() = runTest {
        val store = MemoryTileStore(maxBytes = 20)
        store.put("s", key, bytes(10))
        store.put("s", TileKey(1, 0, 0), bytes(21))
        assertEquals(10, store.get("s", key)?.size)
        assertNull(store.get("s", TileKey(1, 0, 0)))
    }

    @Test
    fun `remove forgets one tile`() = runTest {
        val store = MemoryTileStore()
        store.put("s", key, bytes(10))
        store.put("s", TileKey(1, 0, 0), bytes(10))
        store.remove("s", key)
        assertNull(store.get("s", key))
        assertEquals(10L, store.size())
    }

    @Test
    fun `clearing a source leaves the others and clearing nothing in particular clears all`() = runTest {
        val store = MemoryTileStore()
        store.put("a", key, bytes(10))
        store.put("a/b", key, bytes(10)) // a different source that starts like the first
        store.put("c", key, bytes(10))

        store.clear("a")
        assertNull(store.get("a", key))
        assertEquals(10, store.get("a/b", key)?.size)
        assertEquals(10, store.get("c", key)?.size)
        assertEquals(20L, store.size())

        store.clear()
        assertNull(store.get("c", key))
        assertEquals(0L, store.size())
    }
}
