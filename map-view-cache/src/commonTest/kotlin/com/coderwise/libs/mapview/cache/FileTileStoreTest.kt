package com.coderwise.libs.mapview.cache

import com.coderwise.libs.mapview.TileKey
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileTileStoreTest {

    private val fs = FakeFileSystem()
    private val dir = "/cache".toPath()
    private val key = TileKey(14, 4824, 6159)

    private fun store(maxBytes: Long = 1_000_000) = FileTileStore(fs, dir, maxBytes)
    private fun bytes(size: Int, fill: Int = 1) = ByteArray(size) { fill.toByte() }

    @Test
    fun `what is put is got and what is not is not`() = runTest {
        val store = store()
        assertNull(store.get("streets", key))
        store.put("streets", key, bytes(10, fill = 7))
        assertContentEquals(bytes(10, fill = 7), store.get("streets", key))
        assertNull(store.get("streets", TileKey(14, 4824, 6160)))
    }

    @Test
    fun `a tile is a file at source then z then x then y`() = runTest {
        store().put("streets", key, bytes(3))
        assertTrue(fs.exists("/cache/streets/14/4824/6159.tile".toPath()))
    }

    @Test
    fun `putting again replaces and the size follows`() = runTest {
        val store = store()
        store.put("s", key, bytes(100))
        store.put("s", key, bytes(30, fill = 9))
        assertContentEquals(bytes(30, fill = 9), store.get("s", key))
        assertEquals(30L, store.size())
    }

    @Test
    fun `no half-written file is left behind`() = runTest {
        store().put("s", key, bytes(5))
        val files = fs.listRecursively(dir).filter { fs.metadata(it).isRegularFile }.map { it.name }.toList()
        assertEquals(listOf("6159.tile"), files)
    }

    @Test
    fun `the least recently used goes first`() = runTest {
        val store = store(maxBytes = 30)
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
        assertFalse(fs.exists("/cache/s/1/1/0.tile".toPath()), "the evicted tile is still on disk")
    }

    @Test
    fun `a tile bigger than the whole store is not worth emptying it for`() = runTest {
        val store = store(maxBytes = 20)
        store.put("s", key, bytes(10))
        store.put("s", TileKey(1, 0, 0), bytes(21))
        assertEquals(10, store.get("s", key)?.size)
        assertNull(store.get("s", TileKey(1, 0, 0)))
    }

    @Test
    fun `remove forgets one tile`() = runTest {
        val store = store()
        store.put("s", key, bytes(10))
        store.put("s", TileKey(1, 0, 0), bytes(10))
        store.remove("s", key)
        assertNull(store.get("s", key))
        assertEquals(10L, store.size())
    }

    @Test
    fun `clearing a source leaves the others and clearing nothing in particular clears all`() = runTest {
        val store = store()
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

    @Test
    fun `sources that would collide in a hash do not here`() = runTest {
        val store = store()
        // Different names that a lossy encoding would put in one folder.
        val names = listOf("a/b", "a_b", "a%2Fb", "a b", "A/B", "..", "%2E%2E", ".")
        names.forEachIndexed { i, name -> store.put(name, key, bytes(2, fill = i + 1)) }
        names.forEachIndexed { i, name -> assertContentEquals(bytes(2, fill = i + 1), store.get(name, key), name) }
    }

    @Test
    fun `a source cannot climb out of the cache`() = runTest {
        store().put("../../evil", key, bytes(4))
        val outside = fs.listRecursively("/".toPath()).filter { !it.toString().startsWith("/cache") && fs.metadata(it).isRegularFile }
        assertEquals(emptyList(), outside.toList())
    }

    @Test
    fun `a cache survives the process`() = runTest {
        store().put("s", key, bytes(10, fill = 4))

        val later = store()
        assertContentEquals(bytes(10, fill = 4), later.get("s", key))
        assertEquals(10L, later.size())
    }

    @Test
    fun `a smaller limit than last time is met on the way in`() = runTest {
        val first = store()
        first.put("s", TileKey(1, 0, 0), bytes(10))
        first.put("s", TileKey(1, 1, 0), bytes(10))
        first.put("s", TileKey(1, 0, 1), bytes(10))

        val smaller = store(maxBytes = 20)
        assertEquals(20L, smaller.size())
    }

    @Test
    fun `a file left half-written by a dead process is cleared away`() = runTest {
        fs.createDirectories("/cache/s/1/0".toPath())
        fs.write("/cache/s/1/0/0.tile.partial".toPath()) { write(bytes(8)) }

        val store = store()
        assertEquals(0L, store.size())
        assertFalse(fs.exists("/cache/s/1/0/0.tile.partial".toPath()))
    }

    @Test
    fun `a tile taken away behind the store's back is a miss`() = runTest {
        val store = store()
        store.put("s", key, bytes(10))
        fs.delete("/cache/s/14/4824/6159.tile".toPath())

        assertNull(store.get("s", key))
        assertEquals(0L, store.size())
    }
}
