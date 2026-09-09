package com.coderwise.libs.mapview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A slot is Compose state, and Compose state made on a loader's thread is unreadable to a
 * composition already in flight — so a tile that lands before anything is drawing it has to wait
 * rather than make one.
 */
class MapStateTest {

    private val key = TileKey(3, 1, 2)

    @Test
    fun `a tile that arrives before anyone is drawing it is there on the first read`() {
        val state = MapState<String>()
        state.put(key, "tile")

        assertEquals("tile", state.slot(key))
    }

    @Test
    fun `filling a slot nobody has read makes no slot`() {
        val state = MapState<String>()
        state.put(key, "tile")

        // peek is what a filler asks with, and it sees the tile without a slot being made for it.
        assertEquals("tile", state.peek(key))
        assertTrue(key in state.filled())
    }

    @Test
    fun `a tile that arrives while its cell is drawn lands in the slot`() {
        val state = MapState<String>()
        assertNull(state.slot(key)) // the cell asks first, which is what makes the slot

        state.put(key, "tile")

        assertEquals("tile", state.slot(key))
    }

    @Test
    fun `forgetting a tile forgets it whether it was read or not`() {
        val state = MapState<String>()
        state.put(key, "waiting")
        state.forget(key)
        assertNull(state.peek(key))
        assertNull(state.slot(key))

        state.slot(key)
        state.put(key, "in a slot")
        state.forget(key)
        assertNull(state.peek(key))
    }
}
