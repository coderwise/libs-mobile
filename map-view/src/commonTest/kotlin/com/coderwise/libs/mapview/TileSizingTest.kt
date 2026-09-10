package com.coderwise.libs.mapview

import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tile's measured size must not depend on where the pan sits.
 *
 * The obvious sizing — the gap between a tile's own two snapped edges — makes the tiles cover the
 * plane exactly and changes by a pixel as the pan crosses a boundary, which on a fractional grid
 * spacing is most frames of most drags. Every such change re-measures the tile, and a re-measured
 * tile re-records its drawing: for a vector tile that is thousands of path segments traced again on
 * the UI thread, sixty times a second. Hence these tests.
 */
class TileSizingTest {

    @Test
    fun `the same size at every pan offset`() {
        // 256 dp at density 3 and a third of a zoom level in: as fractional as a grid gets.
        val side = 256.0 * 3f * 1.2599210498948732
        val sizes = (0..200).map { measuredTileSize(side) }.distinct()
        assertEquals(1, sizes.size, "a tile changed size without the spacing changing: $sizes")
    }

    @Test
    fun `snapped edges alone would not be`() {
        // What the size used to be, to show the test above is testing something. Two pan offsets a
        // fraction of a pixel apart, and the gap between the tile's snapped edges differs.
        val side = 384.5
        fun gapAt(origin: Double): Int {
            val left = floor(-origin).toInt()
            return kotlin.math.round(side - origin).toInt() - left
        }
        assertTrue(gapAt(0.0) != gapAt(0.6), "the old sizing was stable after all")
    }

    @Test
    fun `neighbours never leave a gap`() {
        listOf(256.0, 384.5, 704.0, 945.4, 1023.001).forEach { side ->
            val size = measuredTileSize(side)
            (0..8).forEach { column ->
                val left = floor(column * side).toInt()
                val next = floor((column + 1) * side).toInt()
                assertTrue(
                    left + size >= next,
                    "gap of ${next - (left + size)}px after column $column at spacing $side"
                )
            }
        }
    }

    @Test
    fun `and overlap by less than a pixel`() {
        listOf(256.0, 384.5, 945.4).forEach { side ->
            val overlap = measuredTileSize(side) - side
            assertTrue(overlap < 1.0, "overlap of ${overlap}px at spacing $side")
        }
    }
}
