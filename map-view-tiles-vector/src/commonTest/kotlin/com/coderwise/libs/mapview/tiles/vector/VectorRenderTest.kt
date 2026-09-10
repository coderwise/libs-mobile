package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.coderwise.libs.mapview.tiles.vector.mvt.decodeMvt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorRenderTest {

    /** The whole tile — what a slot draws when its own zoom is the one the source carries. */
    private val whole = Rect(0f, 0f, 1f, 1f)

    /** The [sx], [sy] cell of a [cells]x[cells] grid over the tile, as a slot asks for it. */
    private fun cell(cells: Int, sx: Int, sy: Int) =
        Rect(sx.toFloat() / cells, sy.toFloat() / cells, (sx + 1f) / cells, (sy + 1f) / cells)

    @Test
    fun `stroke width scale is the tile box scale for an exact-zoom tile`() {
        assertEquals(257f, strokeWidthScale(scaleX = 257f, scaleY = 257f, src = whole))
    }

    @Test
    fun `stroke width scale averages a non-square tile box`() {
        assertEquals(256f, strokeWidthScale(scaleX = 255f, scaleY = 257f, src = whole))
    }

    @Test
    fun `stroke width scale includes what an over-zoomed ancestor is blown up by`() {
        // z17 drawn off a z14 source: the paths are scaled 8x on top of the box scale, so the
        // stroke has to be divided by 8x as well or the road lands eight times too thick.
        assertEquals(
            256f * 8f,
            strokeWidthScale(scaleX = 256f, scaleY = 256f, src = cell(8, 3, 5))
        )
    }

    // --- srcMatrix -----------------------------------------------------------------------------
    //
    // A cell that resolves to an ancestor draws that ancestor's paths through [srcMatrix]. If the
    // transform puts them anywhere but this cell's box, clipRect drops them and the cell comes out
    // empty — which on screen looks exactly like a tile that never loaded, right down to the
    // ancestor's labels still being registered over the top of it.

    private val box = 256f
    private val scale = box + TILE_BLEED * 2

    /** Where normalised tile coordinate ([nx], [ny]) lands in the slot's pixel box. */
    private fun mapped(src: Rect, nx: Float, ny: Float): Offset =
        srcMatrix(scale, scale, src).map(Offset(nx, ny))

    @Test
    fun `an exact-zoom tile fills its box corner to corner`() {
        val topLeft = mapped(whole, 0f, 0f)
        val bottomRight = mapped(whole, 1f, 1f)
        assertEquals(-TILE_BLEED, topLeft.x, absoluteTolerance = 1e-3f)
        assertEquals(-TILE_BLEED, topLeft.y, absoluteTolerance = 1e-3f)
        assertEquals(box + TILE_BLEED, bottomRight.x, absoluteTolerance = 1e-3f)
        assertEquals(box + TILE_BLEED, bottomRight.y, absoluteTolerance = 1e-3f)
    }

    @Test
    fun `an over-zoomed cell fills its box with its own quadrant of the ancestor`() {
        // The bottom-right quarter of the ancestor, blown up to fill this cell.
        val src = cell(cells = 2, sx = 1, sy = 1)
        val topLeft = mapped(src, 0.5f, 0.5f)
        val bottomRight = mapped(src, 1f, 1f)
        assertEquals(-TILE_BLEED, topLeft.x, absoluteTolerance = 1e-3f)
        assertEquals(-TILE_BLEED, topLeft.y, absoluteTolerance = 1e-3f)
        assertEquals(box + TILE_BLEED, bottomRight.x, absoluteTolerance = 1e-3f)
        assertEquals(box + TILE_BLEED, bottomRight.y, absoluteTolerance = 1e-3f)
    }

    @Test
    fun `an over-zoomed cell leaves the ancestor's other cells outside the box`() {
        val src = cell(cells = 2, sx = 1, sy = 1)
        // The ancestor's top-left quadrant belongs to a different cell and must be clipped away.
        val elsewhere = mapped(src, 0.25f, 0.25f)
        assertTrue(elsewhere.x < 0f, "expected x outside the box, was ${elsewhere.x}")
        assertTrue(elsewhere.y < 0f, "expected y outside the box, was ${elsewhere.y}")
    }

    @Test
    fun `every sub-cell of every depth lands on the box`() {
        // The map over-zooms several cells deep off a z14 source. Every one of those cells has to
        // land on its box: one that doesn't is a permanently blank tile on screen.
        listOf(1, 2, 4, 8).forEach { cells ->
            for (sy in 0 until cells) {
                for (sx in 0 until cells) {
                    val src = cell(cells, sx, sy)
                    val near = mapped(src, sx.toFloat() / cells, sy.toFloat() / cells)
                    val far = mapped(src, (sx + 1f) / cells, (sy + 1f) / cells)
                    assertEquals(-TILE_BLEED, near.x, absoluteTolerance = 1e-2f, "cells=$cells sx=$sx sy=$sy")
                    assertEquals(-TILE_BLEED, near.y, absoluteTolerance = 1e-2f, "cells=$cells sx=$sx sy=$sy")
                    assertEquals(box + TILE_BLEED, far.x, absoluteTolerance = 1e-2f, "cells=$cells sx=$sx sy=$sy")
                    assertEquals(box + TILE_BLEED, far.y, absoluteTolerance = 1e-2f, "cells=$cells sx=$sx sy=$sy")
                }
            }
        }
    }

    // --- open-water tiles ----------------------------------------------------------------------
    //
    // Both of these came out of a device's own tile cache, where they are the smallest tiles in it:
    // open sea, one `water` layer, nothing else. They are the shape of tile the map was drawing as
    // flat background — beige, with the blue of the surrounding ocean stopping at the cell edge —
    // so what they must not do is decode to a RenderTile with nothing in it. A cell whose exact
    // tile is cached never falls back to an ancestor, so an empty decode here is a permanently
    // blank cell.

    /** 58 bytes: a `water` layer holding one polygon covering the tile. */
    private val oceanTile = hexBytes(
        "1a380a057761746572121a08f0f905120200001803220e097f7f1a8042000080" +
        "42ff41000f1a05636c61737322070a056f6365616e2880207802"
    )

    /** 91 bytes: the same, plus the `class`/`intermittent` tags the style keys water on. */
    private val lakeTile = hexBytes(
        "1a590a057761746572121f089be1a11312060000010102021803220e097f7f1a" +
        "804200008042ff41000f1a0c696e7465726d697474656e741a05636c6173731a" +
        "0269642202300022060a046c616b65220530b893ed032880207802"
    )

    private fun hexBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun `an open-water tile decodes to water with features on it`() {
        val style = VectorStyle()
        listOf("ocean" to oceanTile, "lake" to lakeTile).forEach { (name, bytes) ->
            val layers = decodeMvt(bytes, classLayers = VectorStyle.CLASS_AWARE_LAYERS)
            val water = layers.singleOrNull { it.name == "water" }
            assertTrue(water != null, "$name: expected a water layer, got ${layers.map { it.name }}")
            assertTrue(water.features.isNotEmpty(), "$name: water layer decoded with no features")

        }
    }

    @Test
    fun `a road keeps its width whatever depth it is over-zoomed from`() {
        val box = 256f
        val requested = 3.4f
        val drawn = listOf(1, 2, 4, 8).map { cells ->
            // What drawRenderTile hands Stroke, times the total scale the transform applies.
            requested / strokeWidthScale(box, box, cell(cells, 0, 0)) * (box * cells)
        }
        drawn.forEach { assertEquals(requested, it, absoluteTolerance = 1e-4f) }
    }
}
