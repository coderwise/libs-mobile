package com.coderwise.libs.mapview.geojson

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.coderwise.libs.mapview.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [drawGeoJson] into a 100 px image through a projection of the test's own: the middle of the image
 * is 0, 0 and a degree is 10 px, so a square of 4 degrees either side runs from 10 to 90. A path
 * is only real with graphics behind it, so these run on the desktop target.
 */
class DrawTest {

    private val fill = GeoJsonStyle(fill = Color.Red, stroke = Color.Transparent)

    private class Flat(override val zoom: Float = 12f) : GeoJsonProjection {
        override fun trace(path: Path, world: DoubleArray) {
            if (world.size < 4) return
            for (i in world.indices step 2) {
                val x = 50f + ((world[i] - 0.5) * 3600).toFloat()
                val y = 50f + ((world[i + 1] - 0.5) * 3600).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
    }

    private fun render(
        data: FeatureCollection,
        style: GeoJsonStyle = fill,
        minZoom: Float = 0f,
        zoom: Float = 12f
    ): Picture {
        val image = ImageBitmap(100, 100)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(100f, 100f)) {
            drawGeoJson(data, style, Flat(zoom), minZoom)
        }
        return Picture(image)
    }

    private class Picture(image: ImageBitmap) {
        private val pixels = image.toPixelMap()
        operator fun get(x: Int, y: Int) = pixels[x, y]
        fun isEmpty() = (0 until 100).all { y -> (0 until 100).all { x -> this[x, y].alpha == 0f } }
    }

    private fun box(half: Double) = polygon(square(0.0, 0.0, half))

    @Test
    fun `a polygon is filled inside and not outside`() {
        val picture = render(collection(feature(box(4.0))))
        assertEquals(Color.Red, picture[50, 50])
        assertEquals(Color.Red, picture[15, 85])
        assertEquals(0f, picture[5, 50].alpha)
        assertEquals(0f, picture[95, 50].alpha)
    }

    @Test
    fun `a hole is left open`() {
        val picture = render(collection(feature(polygon(square(0.0, 0.0, 4.0), square(0.0, 0.0, 2.0)))))
        assertEquals(0f, picture[50, 50].alpha, "the middle is the hole")
        assertEquals(Color.Red, picture[80, 50], "and the ring round it is not")
        assertEquals(Color.Red, picture[50, 15])
    }

    @Test
    fun `the polygons of a multi-polygon are all drawn`() {
        val picture = render(
            collection(
                feature(Geometry.MultiPolygon(listOf(listOf(square(-2.5, 0.0, 1.5)), listOf(square(2.5, 0.0, 1.5)))))
            )
        )
        assertEquals(Color.Red, picture[25, 50])
        assertEquals(Color.Red, picture[75, 50])
        assertEquals(0f, picture[50, 50].alpha)
    }

    @Test
    fun `features that overlap are both filled where they overlap`() {
        // One even-odd path for the layer would cut the overlap out, as it cuts out a hole.
        val picture = render(
            collection(
                feature(polygon(ring(-4.0, -4.0, 2.0, 4.0))),
                feature(polygon(ring(-2.0, -4.0, 4.0, 4.0)))
            )
        )
        assertEquals(Color.Red, picture[30, 50], "only in the first")
        assertEquals(Color.Red, picture[50, 50], "in both")
        assertEquals(Color.Red, picture[70, 50], "only in the second")
    }

    @Test
    fun `the outline of a polygon is stroked`() {
        val style = GeoJsonStyle(fill = Color.Transparent, stroke = Color.Blue, strokeWidth = 4.dp)
        val picture = render(collection(feature(box(4.0))), style)
        assertEquals(Color.Blue, picture[50, 10], "on the top edge")
        assertEquals(Color.Blue, picture[90, 50], "on the right one")
        assertEquals(0f, picture[50, 50].alpha, "and nothing inside")
    }

    @Test
    fun `a line is stroked and never filled`() {
        val style = GeoJsonStyle(fill = Color.Red, stroke = Color.Blue, strokeWidth = 4.dp)
        val line = render(collection(feature(Geometry.LineString(listOf(LatLon(0.0, -4.0), LatLon(0.0, 4.0))))), style)
        assertEquals(Color.Blue, line[50, 50])
        assertEquals(0f, line[50, 60].alpha)

        val loop = render(collection(feature(Geometry.LineString(square(0.0, 0.0, 4.0)))), style)
        assertEquals(Color.Blue, loop[50, 10], "the loop is drawn")
        assertEquals(0f, loop[50, 50].alpha, "but its inside is not")
    }

    @Test
    fun `a point is a dot as big as its radius`() {
        val style = GeoJsonStyle(pointColor = Color.Green, pointRadius = 5.dp)
        val picture = render(collection(feature(Geometry.Point(LatLon(0.0, 0.0)))), style)
        assertEquals(Color.Green, picture[50, 50])
        assertEquals(Color.Green, picture[53, 50])
        assertEquals(Color.Green, picture[50, 47])
        assertEquals(0f, picture[57, 50].alpha, "beyond the radius")
        assertEquals(0f, picture[54, 54].alpha, "and the corner of its box is not part of a circle")
    }

    @Test
    fun `every point of a multi-point is a dot`() {
        val style = GeoJsonStyle(pointColor = Color.Green, pointRadius = 3.dp)
        val picture = render(collection(feature(Geometry.MultiPoint(listOf(LatLon(0.0, -3.0), LatLon(0.0, 3.0))))), style)
        assertEquals(Color.Green, picture[20, 50])
        assertEquals(Color.Green, picture[80, 50])
        assertEquals(0f, picture[50, 50].alpha)
    }

    @Test
    fun `points are drawn over lines and lines over polygons`() {
        val style = GeoJsonStyle(fill = Color.Red, stroke = Color.Blue, strokeWidth = 6.dp, pointColor = Color.Green, pointRadius = 3.dp)
        // Declared point first and polygon last: the order within a layer is by kind, not by feature.
        val picture = render(
            collection(
                feature(Geometry.Point(LatLon(0.0, 0.0))),
                feature(Geometry.LineString(listOf(LatLon(0.0, -3.0), LatLon(0.0, 3.0)))),
                feature(box(4.0))
            ),
            style
        )
        assertEquals(Color.Green, picture[50, 50], "the point")
        assertEquals(Color.Blue, picture[35, 50], "the line, over the polygon")
        assertEquals(Color.Red, picture[50, 30], "the polygon")
    }

    @Test
    fun `a transparent fill and a width of nothing leave those parts out`() {
        val data = collection(feature(box(4.0)), feature(Geometry.LineString(listOf(LatLon(0.0, -4.0), LatLon(0.0, 4.0)))))
        assertEquals(true, render(data, GeoJsonStyle(fill = Color.Transparent, stroke = Color.Blue, strokeWidth = 0.dp)).isEmpty())
        assertEquals(true, render(data, GeoJsonStyle(fill = Color.Transparent, stroke = Color.Transparent)).isEmpty())
        assertEquals(true, render(collection(feature(Geometry.Point(LatLon(0.0, 0.0)))), GeoJsonStyle(pointRadius = 0.dp)).isEmpty())
    }

    @Test
    fun `nothing is drawn under the minimum zoom`() {
        val data = collection(feature(box(4.0)), feature(Geometry.Point(LatLon(0.0, 0.0))))
        assertEquals(true, render(data, minZoom = 12f, zoom = 11.9f).isEmpty())
        assertEquals(false, render(data, minZoom = 12f, zoom = 12f).isEmpty(), "and it is there from the zoom itself")
    }

    @Test
    fun `features with nothing to draw are no trouble`() {
        assertEquals(true, render(collection(feature(null), feature(Geometry.MultiPoint(emptyList())), feature(polygon()))).isEmpty())
        assertEquals(true, render(collection()).isEmpty())
    }
}
