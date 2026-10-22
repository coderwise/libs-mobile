package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [drawVectorLabels] outside any composition: a canvas, a measurer built by hand, and the pixels
 * that come out. Names are painted over a transparent bitmap, so "something was drawn" is simply a
 * pixel that is no longer transparent.
 */
class DrawVectorLabelsTest {

    private val side = 256

    private val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)

    private val whole = Rect(0f, 0f, 1f, 1f)

    private fun tileWith(vararg labels: Label) = VectorTile(
        render = RenderTile(emptyList()),
        labels = labels.toList(),
        background = Color.Transparent,
        ink = Color.Black,
        halo = Color.White,
        waterInk = Color.Blue,
        water = null,
        zoom = 14
    )

    private fun draw(tile: VectorTile, src: Rect, bearing: Float = 0f): ImageBitmap {
        val bitmap = ImageBitmap(side, side)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
            drawVectorLabels(tile, src, measurer, bearing)
        }
        return bitmap
    }

    private fun ImageBitmap.inked(): Int {
        val pixels = toPixelMap()
        var count = 0
        for (y in 0 until height) for (x in 0 until width) if (pixels[x, y].alpha > 0f) count++
        return count
    }

    @Test
    fun `a name inside the source is drawn`() {
        val tile = tileWith(Label(0.5f, 0.5f, "Reykjavik", 20f))
        assertTrue(draw(tile, whole).inked() > 0, "nothing was drawn")
    }

    @Test
    fun `a water name is drawn in the water ink and a place name in the ink`() {
        fun fillColours(label: Label): Set<Color> {
            val pixels = draw(tileWith(label), whole).toPixelMap()
            val seen = mutableSetOf<Color>()
            for (y in 0 until side) for (x in 0 until side) {
                val pixel = pixels[x, y]
                if (pixel.alpha == 1f) seen += pixel
            }
            return seen
        }
        // Fully opaque pixels are the fill; the halo is white and the edges are blends.
        val place = fillColours(Label(0.5f, 0.5f, "Reykjavik", 20f))
        val water = fillColours(Label(0.5f, 0.5f, "Reykjavik", 20f, water = true))
        assertTrue(Color.Black in place && Color.Blue !in place, "place: $place")
        assertTrue(Color.Blue in water && Color.Black !in water, "water: $water")
    }

    @Test
    fun `a tile with no names draws nothing`() {
        assertEquals(0, draw(tileWith(), whole).inked())
    }

    @Test
    fun `a name outside the source is somebody else's and is not drawn`() {
        val tile = tileWith(Label(0.9f, 0.9f, "Reykjavik", 20f))
        assertEquals(0, draw(tile, Rect(0f, 0f, 0.5f, 0.5f)).inked())
    }

    @Test
    fun `the source is stretched over the scope, so a quarter of the tile shows its name big`() {
        val name = Label(0.25f, 0.25f, "Reykjavik", 20f)
        val inked = draw(tileWith(name), Rect(0f, 0f, 0.5f, 0.5f)).toPixelMap()
        // Centred on a quarter of the tile that fills the whole scope: the middle of the scope.
        var near = false
        for (y in side / 2 - 12..side / 2 + 12) for (x in side / 2 - 12..side / 2 + 12) {
            if (inked[x, y].alpha > 0f) near = true
        }
        assertTrue(near, "no ink around the middle of the scope")
    }

    @Test
    fun `an empty source draws nothing and does not divide by zero`() {
        val tile = tileWith(Label(0.5f, 0.5f, "Reykjavik", 20f))
        assertEquals(0, draw(tile, Rect(0.5f, 0.5f, 0.5f, 0.5f)).inked())
    }

    @Test
    fun `a place name stays level however the map is turned`() {
        val tile = tileWith(Label(0.5f, 0.5f, "Reykjavik", 20f))
        val level = draw(tile, whole, bearing = 0f).inked()
        val turned = draw(tile, whole, bearing = 90f).inked()
        // Turned back by the bearing it was turned by, so the same text lands: not equal pixel for
        // pixel after antialiasing at a different angle, but the same amount of ink to a few percent.
        assertTrue(kotlin.math.abs(level - turned) < level / 10, "level=$level turned=$turned")
    }
}
