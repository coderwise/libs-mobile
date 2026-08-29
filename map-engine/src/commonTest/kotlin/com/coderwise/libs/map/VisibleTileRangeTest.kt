package com.coderwise.libs.map

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ring of off-screen tiles around the viewport is panning headroom, and a map that cannot be
 * panned should not be laid out with one. On a small viewport the ring is most of the grid: a card
 * preview covers a couple of cells and the ring around it multiplies that several times over, with
 * every extra cell fetched, decoded and drawn.
 */
class VisibleTileRangeTest {

    /** A 360x160dp card preview on a 3x phone, with 256.dp tiles. */
    private fun previewState() = TiledMapState(
        initialLatitude = 52.52,
        initialLongitude = 13.40,
        initialZoom = 12.0,
        initialTileSizePx = 768,
    )

    private val previewSize = IntSize(1080, 480)

    private fun TileRange.cellCount() = (maxX - minX + 1) * (maxY - minY + 1)

    @Test
    fun `a preview-sized viewport is mostly margin when it keeps one`() {
        val state = previewState()
        val centre = center(state)

        val withMargin = calculateVisibleTileRange(previewSize, centre, state, margin = 1)!!
        val without = calculateVisibleTileRange(previewSize, centre, state, margin = 0)!!

        // The viewport is 1.4 cells across and 0.6 down, so it straddles 3 by 2. A ring around
        // that is 5 by 4 — the same handful of visible tiles, and fourteen more to pay for.
        assertEquals(6, without.cellCount())
        assertEquals(20, withMargin.cellCount())

        // Stated as the relationship rather than the two numbers, since those depend on where the
        // pan happens to sit: on a viewport this size the ring is most of the grid, whatever the
        // phase.
        assertTrue(
            withMargin.cellCount() >= 3 * without.cellCount(),
            "expected the ring to dominate a preview-sized grid"
        )
    }

    @Test
    fun `dropping the margin still covers every cell the viewport touches`() {
        val state = previewState()
        // Sweep the pan across a whole tile, since where the centre falls inside a cell is what
        // decides how many cells the viewport straddles.
        for (step in 0 until 32) {
            val centre = center(state).let { (x, y) -> (x + step / 32.0) to (y + step / 64.0) }
            val range = calculateVisibleTileRange(previewSize, centre, state, margin = 0)!!

            val halfWidthTiles = (previewSize.width / 2.0) / state.scaledTileSizePx
            val halfHeightTiles = (previewSize.height / 2.0) / state.scaledTileSizePx
            assertTrue(
                range.minX <= kotlin.math.floor(centre.first - halfWidthTiles) &&
                    range.maxX >= kotlin.math.floor(centre.first + halfWidthTiles) &&
                    range.minY <= kotlin.math.floor(centre.second - halfHeightTiles) &&
                    range.maxY >= kotlin.math.floor(centre.second + halfHeightTiles),
                "step $step left part of the viewport uncovered: $range"
            )
        }
    }

    @Test
    fun `a full-screen viewport keeps its ring for panning`() {
        val state = TiledMapState(
            initialLatitude = 52.52,
            initialLongitude = 13.40,
            initialZoom = 12.0,
            initialTileSizePx = 768,
        )
        val phone = IntSize(1080, 2400)

        val withMargin = calculateVisibleTileRange(phone, center(state), state, margin = 1)!!
        val without = calculateVisibleTileRange(phone, center(state), state, margin = 0)!!

        assertTrue(
            withMargin.cellCount() > without.cellCount(),
            "the ring must still be there for a map that can be dragged"
        )
        // Exactly one cell on each side, and nothing else changed.
        assertEquals(without.minX - 1, withMargin.minX)
        assertEquals(without.maxX + 1, withMargin.maxX)
        assertEquals(without.minY - 1, withMargin.minY)
        assertEquals(without.maxY + 1, withMargin.maxY)
    }

    @Test
    fun `an unmeasured viewport has no range at all`() {
        val state = previewState()
        assertNull(calculateVisibleTileRange(IntSize.Zero, center(state), state, margin = 0))
        assertNull(calculateVisibleTileRange(IntSize(1080, 0), center(state), state, margin = 1))
    }

    private fun center(state: TiledMapState): Pair<Double, Double> =
        com.coderwise.libs.mapcore.MapMath.latLonToTileFractional(
            state.latitude, state.longitude, state.zoomInt.toDouble()
        )
}
