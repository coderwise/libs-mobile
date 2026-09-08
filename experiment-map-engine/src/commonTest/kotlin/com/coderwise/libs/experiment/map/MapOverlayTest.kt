package com.coderwise.libs.experiment.map

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapOverlayTest {

    private val london = LatLon(51.5074, -0.1278)

    private fun overlay(
        center: LatLon = london,
        zoom: Float = 12f,
        width: Float = 1080f,
        height: Float = 1920f,
        density: Float = 1f
    ) = MapOverlayState(MapCameraState(center, zoom)).apply { measured(width, height, density) }

    @Test
    fun `what the camera is looking at is in the middle of the map`() {
        val point = overlay().project(london)
        assertEquals(540f, point.x, 1e-2f)
        assertEquals(960f, point.y, 1e-2f)
    }

    @Test
    fun `unproject undoes project`() {
        val overlay = overlay()
        for (offset in listOf(Offset(0f, 0f), Offset(1080f, 1920f), Offset(210f, 1500f))) {
            val there = overlay.unproject(offset)
            val back = overlay.project(there)
            assertEquals(offset.x, back.x, 1e-2f, "x of $offset")
            assertEquals(offset.y, back.y, 1e-2f, "y of $offset")
        }
    }

    @Test
    fun `a point due east is to the right of one due west`() {
        val overlay = overlay()
        assertTrue(overlay.project(LatLon(51.5074, 0.1)).x > overlay.project(LatLon(51.5074, -0.4)).x)
    }

    @Test
    fun `a point beyond the antimeridian comes round the near side`() {
        // Looking at Fiji: 179 is a degree to the west, not most of the world away.
        val overlay = overlay(center = LatLon(-18.0, -180.0), zoom = 6f)
        val point = overlay.project(LatLon(-18.0, 179.0))
        assertTrue(abs(point.x - 540f) < 540f, "expected 179E on screen, was ${point.x}")
        assertTrue(point.x < 540f, "expected 179E to the west of the camera, was ${point.x}")
    }

    @Test
    fun `density magnifies the map without moving its middle`() {
        val one = overlay(density = 1f).project(LatLon(51.6, 0.0))
        val two = overlay(density = 2f).project(LatLon(51.6, 0.0))
        assertEquals(2 * (one.x - 540f), two.x - 540f, 1e-1f)
        assertEquals(2 * (one.y - 960f), two.y - 960f, 1e-1f)
    }
}
