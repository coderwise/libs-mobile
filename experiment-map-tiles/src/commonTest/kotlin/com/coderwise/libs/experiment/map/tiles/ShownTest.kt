package com.coderwise.libs.experiment.map.tiles

import androidx.compose.ui.geometry.Rect
import com.coderwise.libs.experiment.map.MapState
import com.coderwise.libs.experiment.map.TileKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShownTest {

    private val state = MapState<String>()

    @Test
    fun `an empty slot shows nothing`() {
        assertNull(state.shown(TileKey(14, 8186, 5454)))
    }

    @Test
    fun `its own tile is shown whole`() {
        val key = TileKey(14, 8186, 5454)
        state.put(key, "tile")
        assertEquals(Shown(key, "tile", Rect(0f, 0f, 1f, 1f)), state.shown(key))
    }

    @Test
    fun `the parent stands in for a quarter of itself`() {
        state.put(TileKey(13, 4093, 2727), "parent")
        // 8186 and 5454 are both even, so this is the parent's top left quarter.
        assertEquals(
            Shown(TileKey(13, 4093, 2727), "parent", Rect(0f, 0f, 0.5f, 0.5f)),
            state.shown(TileKey(14, 8186, 5454))
        )
        assertEquals(
            Shown(TileKey(13, 4093, 2727), "parent", Rect(0.5f, 0.5f, 1f, 1f)),
            state.shown(TileKey(14, 8187, 5455))
        )
    }

    @Test
    fun `the nearest ancestor wins`() {
        state.put(TileKey(12, 2046, 1363), "grandparent")
        state.put(TileKey(13, 4093, 2727), "parent")
        assertEquals(TileKey(13, 4093, 2727), state.shown(TileKey(14, 8186, 5454))?.key)
    }

    @Test
    fun `an ancestor further up than the limit is not shown`() {
        state.put(TileKey(8, 127, 85), "far")
        assertNull(state.shown(TileKey(14, 8186, 5454)))
        assertEquals(TileKey(8, 127, 85), state.shown(TileKey(14, 8186, 5454), levels = 6)?.key)
    }
}
