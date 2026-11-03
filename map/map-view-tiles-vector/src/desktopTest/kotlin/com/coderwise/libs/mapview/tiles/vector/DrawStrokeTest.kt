package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.coderwise.libs.mapview.tiles.vector.mvt.GeometryType
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtFeature
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtLayer
import com.coderwise.libs.mapview.tiles.vector.mvt.TileGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a style's line paint comes out as, drawn for real: a dash is a pattern of gaps along a row of
 * pixels, and a detail-aware road lookup is a different colour on two lines the plain one would
 * paint alike.
 */
class DrawStrokeTest {

    private val side = 256
    private val extent = 4096

    private fun horizontal(y: Int, vararg details: String?) = MvtFeature(
        type = GeometryType.LINE,
        geometry = TileGeometry(intArrayOf(0, y, extent, y), intArrayOf(0)),
        featureClass = "footway",
        details = details.toList()
    )

    private fun renderTile(
        style: VectorStyle,
        vararg features: MvtFeature,
        detailTags: List<String> = listOf("subclass", "brunnel")
    ) = buildRenderTile(listOf(MvtLayer("transportation", extent, features.toList(), detailTags)), style)

    private fun draw(tile: RenderTile, px: Int = side): PixelMap {
        val bitmap = ImageBitmap(px, px)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(px.toFloat(), px.toFloat())
        ) {
            drawRenderTile(tile, Color.Transparent, zoom = 14, src = Rect(0f, 0f, 1f, 1f))
        }
        return bitmap.toPixelMap()
    }

    private fun draw(style: VectorStyle, vararg features: MvtFeature): PixelMap =
        draw(renderTile(style, *features))

    /** How many separate runs of ink the row [y] has between the tile's edges. */
    private fun PixelMap.runs(y: Int): Int {
        var runs = 0
        var on = false
        for (x in 8 until width - 8) {
            val ink = this[x, y].alpha > 0.5f
            if (ink && !on) runs++
            on = ink
        }
        return runs
    }

    private val solid = LayerStyle(stroke = Color.Red, strokeWidth = ZoomWidth(4.dp), cap = StrokeCap.Butt)

    @Test
    fun `a solid line is one run across the tile`() {
        val pixels = draw(VectorStyle(road = { solid }), horizontal(extent / 2))
        assertEquals(1, pixels.runs(side / 2))
    }

    @Test
    fun `a dashed line is many, and the gaps are the length asked for`() {
        val dashed = solid.copy(dash = listOf(10.dp, 6.dp))
        val pixels = draw(VectorStyle(road = { dashed }), horizontal(extent / 2))
        // 240 px at a 16 px period is fifteen dashes, give or take the ends.
        assertTrue(pixels.runs(side / 2) in 13..16, "runs: ${pixels.runs(side / 2)}")
        // The first dash starts at the line's start and is 10 px long: ink at 5, a gap at 13.
        assertTrue(pixels[5, side / 2].alpha > 0.5f, "no ink inside the first dash")
        assertTrue(pixels[13, side / 2].alpha < 0.5f, "ink inside the first gap")
    }

    @Test
    fun `dashing a line leaves it as thick as the width asked for`() {
        val dashed = solid.copy(dash = listOf(10.dp, 6.dp))
        val pixels = draw(VectorStyle(road = { dashed }), horizontal(extent / 2))
        // Column 5 is inside the first dash; the line is 4 px wide.
        val thickness = (0 until side).count { pixels[5, it].alpha > 0.5f }
        assertTrue(thickness in 3..5, "thickness: $thickness")
    }

    @Test
    fun `one tile drawn at two sizes dashes to the same length on each`() {
        // The dash is kept from one draw to the next, and what it depends on changed between them:
        // a stale effect would carry the first size's pattern into the second.
        val dashed = solid.copy(dash = listOf(10.dp, 6.dp))
        val tile = renderTile(VectorStyle(road = { dashed }), horizontal(extent / 2))
        val big = draw(tile, 256).runs(128)
        val small = draw(tile, 128).runs(64)
        assertTrue(big in 13..16, "big: $big")
        // 112 px at a 16 px period is seven dashes.
        assertTrue(small in 6..8, "small: $small")
        // And back again: the first size is not remembered wrongly either.
        assertEquals(big, draw(tile, 256).runs(128))
    }

    @Test
    fun `roadDetail reads the tags by name, whatever order they were decoded in`() {
        val tunnel = solid.copy(stroke = Color.Green)
        val style = VectorStyle(
            road = { solid },
            roadDetail = { _, subclass, brunnel -> if (brunnel == "tunnel" && subclass == "link") tunnel else null }
        )
        // Decoded as brunnel then subclass: the opposite of the order the style's defaults use.
        val swapped = renderTile(
            style,
            horizontal(extent / 2, "tunnel", "link"),
            detailTags = listOf("brunnel", "subclass")
        )
        assertEquals(Color.Green, draw(swapped)[side / 2, side / 2].copy(alpha = 1f))
        // A layer that decoded neither has nothing to ask about, and falls back to road.
        val none = renderTile(style, horizontal(extent / 2, "tunnel", "link"), detailTags = emptyList())
        assertEquals(Color.Red, draw(none)[side / 2, side / 2].copy(alpha = 1f))
    }

    @Test
    fun `roadDetail paints two lines of one class differently`() {
        val tunnel = solid.copy(stroke = Color.Green)
        val style = VectorStyle(
            road = { solid },
            roadDetail = { _, _, brunnel -> if (brunnel == "tunnel") tunnel else null }
        )
        val pixels = draw(
            style,
            horizontal(extent / 4, null, "tunnel"),
            horizontal(extent * 3 / 4, null, null)
        )
        assertEquals(Color.Green, pixels[side / 2, side / 4].copy(alpha = 1f))
        assertEquals(Color.Red, pixels[side / 2, side * 3 / 4].copy(alpha = 1f))
    }
}
