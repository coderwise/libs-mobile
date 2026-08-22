package com.coderwise.libs.map

import kotlin.math.floor
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tiles must hold one size for a whole gesture. A tile whose measured size changes is re-measured,
 * and a re-measured tile has its graphics layer invalidated — so the grid re-records all of its
 * drawing on the UI thread on every frame of a drag instead of moving layers it has already drawn.
 * On a vector map that was the difference between ~150 ms and ~32 ms a frame.
 */
class TileSizingTest {

    @Test
    fun `tile size does not follow the pan, as the floored gap between edges did`() {
        // A 420-dpi phone at zoom 14.68: 256.dp is 672 px, and zoomScale is 2^0.68, so the grid
        // spacing is not a whole number. It is a whole number only at an exactly integer zoom,
        // which a pinch never leaves the map at.
        val spacing = 672 * 2.0.pow(0.68)
        val flooredGaps = HashSet<Int>()
        val measured = HashSet<Int>()

        var center = 1000.0
        repeat(500) {
            for (tx in 1000..1005) {
                flooredGaps += floor((tx + 1 - center) * spacing).toInt() -
                    floor((tx - center) * spacing).toInt()
                measured += measuredTileSize(spacing)
            }
            center += 0.011
        }

        assertEquals(
            2, flooredGaps.size,
            "the edge-gap sizing this replaced is expected to flip between two widths as it pans"
        )
        assertEquals(1, measured.size, "a tile must keep one size across the whole pan")
    }

    @Test
    fun `a whole-number spacing is left exactly alone, so tiles still abut`() {
        // At an integer zoom the spacing is already whole and rounding up is the identity — the
        // sub-pixel overlap only appears where it buys the stable size.
        assertEquals(672, measuredTileSize(672.0))
        assertEquals(512, measuredTileSize(512.0))
    }
}
