package com.coderwise.libs.mapview

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapHitTest {

    private val london = LatLon(51.5074, -0.1278)

    private fun overlay(zoom: Float = 13f) =
        MapOverlayState(MapCameraState(london, zoom)).apply { measured(1080f, 1920f, 1f) }

    private fun world(points: List<LatLon>) = DoubleArray(points.size * 2).also {
        points.forEachIndexed { i, point ->
            it[i * 2] = Mercator.x(point.lon)
            it[i * 2 + 1] = Mercator.y(point.lat)
        }
    }

    @Test
    fun `the distance to a segment is measured to its ends beyond them`() {
        val from = Offset(0f, 0f)
        val to = Offset(100f, 0f)
        assertEquals(0f, distanceToSegment(Offset(50f, 0f), from, to), 1e-3f)
        assertEquals(10f, distanceToSegment(Offset(50f, 10f), from, to), 1e-3f)
        // Off the end: to the end itself, not to the line it lies on.
        assertEquals(30f, distanceToSegment(Offset(130f, 0f), from, to), 1e-3f)
        assertEquals(5f, distanceToSegment(Offset(-3f, 4f), from, to), 1e-3f)
    }

    @Test
    fun `a tap on the line is on it and one a street away is not`() {
        val overlay = overlay()
        val line = world(listOf(LatLon(51.5000, -0.14), LatLon(51.5150, -0.11)))
        val middle = overlay.project(LatLon(51.5075, -0.125))
        assertTrue(overlay.distanceTo(line, middle) < 2f, "middle of the line")
        assertTrue(overlay.distanceTo(line, middle + Offset(0f, 200f)) > 100f, "200 px off it")
    }

    @Test
    fun `the line is only as long as its points`() {
        val overlay = overlay()
        val ends = listOf(LatLon(51.5000, -0.14), LatLon(51.5050, -0.13))
        val line = world(ends)
        // Carrying on in the same direction leaves the line behind rather than following it.
        val beyond = overlay.project(LatLon(51.5150, -0.11))
        assertTrue(overlay.distanceTo(line, beyond) > 100f, "past the end")
    }

    @Test
    fun `zooming in moves a tap further from the line`() {
        val at = LatLon(51.5100, -0.1278)
        val line = world(listOf(LatLon(51.5000, -0.1278), LatLon(51.5050, -0.1278)))
        val close = overlay(zoom = 11f).let { it.distanceTo(line, it.project(at)) }
        val far = overlay(zoom = 13f).let { it.distanceTo(line, it.project(at)) }
        assertTrue(far > close * 3, "expected the gap to grow with the zoom, was $close then $far")
    }
}
