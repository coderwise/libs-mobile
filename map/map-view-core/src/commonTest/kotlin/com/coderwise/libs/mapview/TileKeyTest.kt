package com.coderwise.libs.mapview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TileKeyTest {

    @Test
    fun `a key is its level column and row`() {
        val key = TileKey(14, 4824, 6159)
        assertEquals(14, key.z)
        assertEquals(4824, key.x)
        assertEquals(6159, key.y)
    }

    @Test
    fun `two keys for the same tile are one key`() {
        // What lets a tile be a map key and a set member.
        assertEquals(TileKey(3, 1, 2), TileKey(3, 1, 2))
        assertEquals(TileKey(3, 1, 2).hashCode(), TileKey(3, 1, 2).hashCode())
        assertEquals(1, setOf(TileKey(3, 1, 2), TileKey(3, 1, 2)).size)
    }

    @Test
    fun `a different column row or level is a different tile`() {
        val key = TileKey(3, 1, 2)
        assertNotEquals(key, TileKey(4, 1, 2))
        assertNotEquals(key, TileKey(3, 2, 2))
        assertNotEquals(key, TileKey(3, 1, 3))
    }

    @Test
    fun `a key can be copied with one part changed`() {
        assertEquals(TileKey(3, 1, 5), TileKey(3, 1, 2).copy(y = 5))
    }
}
