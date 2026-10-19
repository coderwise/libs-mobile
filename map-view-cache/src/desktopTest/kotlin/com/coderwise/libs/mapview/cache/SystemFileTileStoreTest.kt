package com.coderwise.libs.mapview.cache

import com.coderwise.libs.mapview.TileKey
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The same store over the file system it is for, since a fake only agrees with itself. */
class SystemFileTileStoreTest {

    private val fs = FileSystem.SYSTEM
    private val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "map-view-cache-test-${Random.nextLong().toULong()}"
    private val key = TileKey(14, 4824, 6159)

    @AfterTest
    fun cleanUp() = fs.deleteRecursively(dir, mustExist = false)

    @Test
    fun `a tile round trips through a real file`() = runTest {
        val store = FileTileStore(fs, dir)
        store.put("streets", key, byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), store.get("streets", key))
        assertEquals(3L, store.size())
    }

    @Test
    fun `putting over a tile replaces it`() = runTest {
        val store = FileTileStore(fs, dir)
        store.put("s", key, byteArrayOf(1, 2, 3))
        store.put("s", key, byteArrayOf(9))
        assertContentEquals(byteArrayOf(9), store.get("s", key))
        assertEquals(1L, store.size())
    }

    @Test
    fun `a cache is there for the next run, and eviction takes files with it`() = runTest {
        FileTileStore(fs, dir, maxBytes = 20).apply {
            put("s", TileKey(1, 0, 0), ByteArray(10))
            put("s", TileKey(1, 1, 0), ByteArray(10))
            put("s", TileKey(1, 0, 1), ByteArray(10)) // the first is evicted
        }

        val later = FileTileStore(fs, dir, maxBytes = 20)
        assertEquals(20L, later.size())
        assertNull(later.get("s", TileKey(1, 0, 0)))
        assertEquals(10, later.get("s", TileKey(1, 1, 0))?.size)
        assertEquals(10, later.get("s", TileKey(1, 0, 1))?.size)
        assertEquals(2, fs.listRecursively(dir).count { fs.metadata(it).isRegularFile })
    }

    @Test
    fun `clearing empties the directory`() = runTest {
        val store = FileTileStore(fs, dir)
        store.put("a", key, byteArrayOf(1))
        store.put("b", key, byteArrayOf(1))
        store.clear()
        assertEquals(0L, store.size())
        assertNull(store.get("a", key))
        assertEquals(false, fs.exists(dir / "a"))
    }
}
