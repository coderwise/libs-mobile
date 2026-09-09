package com.coderwise.libs.mapview

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BearingTest {

    private val london = LatLon(51.5074, -0.1278)

    private fun overlay(bearing: Float) =
        MapOverlayState(MapCameraState(london, zoom = 12f, bearing = bearing))
            .apply { measured(1000f, 1000f, 1f) }

    @Test
    fun `bearing is what is at the top of the screen`() {
        val east = LatLon(51.5074, 0.1)
        // North up: a point to the east is to the right of the middle.
        overlay(0f).project(east).let {
            assertTrue(it.x > 500f, "east should be right of centre, was ${it.x}")
            assertEquals(500f, it.y, 1f)
        }
        // East up: the same point is above it.
        overlay(90f).project(east).let {
            assertEquals(500f, it.x, 1f)
            assertTrue(it.y < 500f, "east should be above centre, was ${it.y}")
        }
    }

    @Test
    fun `unproject undoes project at any bearing`() {
        for (bearing in listOf(0f, 37f, 90f, 180f, 315f)) {
            val overlay = overlay(bearing)
            for (offset in listOf(Offset(0f, 0f), Offset(1000f, 250f), Offset(410f, 900f))) {
                val back = overlay.project(overlay.unproject(offset))
                assertEquals(offset.x, back.x, 1e-2f, "x of $offset at $bearing")
                assertEquals(offset.y, back.y, 1e-2f, "y of $offset at $bearing")
            }
        }
    }

    @Test
    fun `a drag follows the finger whichever way the map is turned`() {
        // North up: dragging the map up shows what was below it, so the camera goes south.
        MapCameraState(london, zoom = 12f).let {
            it.pan(0f, -100f, density = 1f)
            assertTrue(it.center.lat < london.lat, "expected to move south, went to ${it.center}")
            assertEquals(london.lon, it.center.lon, 1e-6)
        }
        // East up: the same drag goes the same way *on screen*, which is now west.
        MapCameraState(london, zoom = 12f, bearing = 90f).let {
            it.pan(0f, -100f, density = 1f)
            assertTrue(it.center.lon < london.lon, "expected to move west, went to ${it.center}")
            assertEquals(london.lat, it.center.lat, 1e-6)
        }
        // And pulling the map down looks ahead, east.
        MapCameraState(london, zoom = 12f, bearing = 90f).let {
            it.pan(0f, 100f, density = 1f)
            assertTrue(it.center.lon > london.lon, "expected to move east, went to ${it.center}")
        }
    }

    @Test
    fun `turning keeps the point under the finger where it is`() {
        val camera = MapCameraState(london, zoom = 12f)
        val overlay = MapOverlayState(camera).apply { measured(1000f, 1000f, 1f) }
        val focus = Offset(300f, 700f)
        val there = overlay.unproject(focus)
        camera.rotateBy(40f, focus.x, focus.y, 1000f, 1000f, density = 1f)
        val after = overlay.project(there)
        assertEquals(focus.x, after.x, 1e-1f)
        assertEquals(focus.y, after.y, 1e-1f)
    }

    @Test
    fun `the plane is the box the turned viewport needs`() {
        assertEquals(IntSizeOf(400, 800), planeSize(400, 800, 0f).let { IntSizeOf(it.width, it.height) })
        // A quarter turn swaps the sides, give or take the rounding that keeps it from falling short.
        planeSize(400, 800, 90f).let {
            assertTrue(abs(it.width - 800) <= 1 && abs(it.height - 400) <= 1, "was $it")
        }
        // Anything in between is bigger than either.
        planeSize(400, 800, 45f).let {
            assertTrue(it.width > 800 && it.height > 800, "was $it")
        }
    }

    @Test
    fun `a turned map asks for the tiles the corners need`() {
        val state = MapState<Unit>(zoomRange = 0..19)
        val straight = tileGrid(MapCameraState(london, 12f), 1000f, 1000f, 1f, state)
        val turned = planeSize(1000, 1000, 45f).let {
            tileGrid(MapCameraState(london, 12f, bearing = 45f), it.width.toFloat(), it.height.toFloat(), 1f, state)
        }
        assertTrue(
            turned.window.keys.size > straight.window.keys.size,
            "a turned viewport reaches further: ${turned.window.keys.size} vs ${straight.window.keys.size}"
        )
        assertTrue(straight.window.keys.all { it in turned.window.keys }, "and still covers the middle")
    }
}

private data class IntSizeOf(val width: Int, val height: Int)
