package com.coderwise.libs.mapview

import androidx.compose.ui.unit.Constraints
import kotlin.math.ceil
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
        SPACINGS.forEach { side ->
            val size = measuredTileSize(side)
            (0..8).forEach { step ->
                val left = gridStep(step, side)
                val next = gridStep(step + 1, side)
                assertTrue(
                    left + size >= next,
                    "gap of ${next - (left + size)}px after step $step at spacing $side"
                )
            }
        }
    }

    @Test
    fun `and the grid reaches both edges of the plane wherever the pan sits`() {
        // What the plane and the layer above it do between them: tiles laid out from the window's
        // own origin, and the pan applied as a translation. The two have to cover the viewport
        // between them at every offset, or a sliver of nothing shows along an edge.
        SPACINGS.forEach { side ->
            val size = measuredTileSize(side)
            (0..400).forEach { tick ->
                val origin = tick * side / 97.0 - 3 * side
                listOf(1080f, 1440f, 2400f).forEach { width ->
                    val first = floor(origin / side).toInt()
                    val last = ceil((origin + width) / side).toInt() - 1
                    val pan = first * side - origin // what panTranslation hands the layer
                    assertTrue(
                        gridStep(0, side) + pan <= 0.0,
                        "${-(gridStep(0, side) + pan)}px uncovered at the near edge, spacing $side"
                    )
                    assertTrue(
                        gridStep(last - first, side) + size + pan >= width,
                        "${width - (gridStep(last - first, side) + size + pan)}px uncovered at the " +
                            "far edge of a ${width}px plane, spacing $side"
                    )
                }
            }
        }
    }

    /** Spacings a real grid lands on: whole, half, and as fractional as a third of a level gets. */
    private val SPACINGS = listOf(256.0, 384.5, 704.0, 945.4, 1023.001, 967.9384)

    /**
     * A source that stops at z14 drawn at zoom 20 on a 2x screen spaces its tiles 32768px apart,
     * which `Constraints` cannot hold in both axes — it used to throw while a track card composed.
     * The box a tile is measured in has to stay inside what layout can represent, and the tile has
     * to still reach its neighbour once the layer has scaled it back up.
     */
    @Test
    fun `an over-zoomed tile is measured small enough to lay out`() {
        // Every over-zoom a z14 source can be asked for, at every density a screen has.
        listOf(1f, 2f, 3f, 4f).forEach { density ->
            (0..8).forEach { overZoom ->
                val spacing = tileSpacing(14f + overZoom, 14, density)
                val magnification = tileMagnification(spacing)
                val side = measuredTileSize(spacing / magnification)

                Constraints.fixed(side, side) // what TilePlane measures with; used to throw
                assertTrue(
                    side * magnification >= spacing,
                    "a tile ${spacing - side * magnification}px short of its cell at density " +
                        "$density, over-zoom $overZoom"
                )
            }
        }
    }

    @Test
    fun `and is not scaled at all until it has to be`() {
        SPACINGS.forEach { side ->
            assertEquals(1.0, tileMagnification(side), "spacing $side was scaled needlessly")
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
