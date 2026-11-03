package com.coderwise.libs.mapview

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [fitCamera] checked against the camera it is meant to drive: whatever it answers, a
 * [MapCameraState] put there has to show the points where the fit says they are.
 */
class CameraFitTest {

    private val portrait = Size(400f, 800f)
    private val landscape = Size(1000f, 300f)

    private val london = LatLon(51.5, -0.12)
    private val paris = LatLon(48.86, 2.35)
    private val berlin = LatLon(52.52, 13.4)

    private fun camera(fit: CameraFit, bearing: Float = 0f) =
        MapCameraState(fit.center, fit.zoom, bearing)

    /** The smallest distance from [at] to an edge of the padded box, negative if it is outside. */
    private fun room(
        camera: MapCameraState,
        at: LatLon,
        size: Size,
        density: Float,
        paddingPx: Float
    ): Float {
        val on = camera.screenPosition(at, size.width, size.height, density)
        return minOf(on.x - paddingPx, size.width - paddingPx - on.x, on.y - paddingPx, size.height - paddingPx - on.y)
    }

    // --- what comes back ---------------------------------------------------------------------------

    @Test
    fun `no points frame nothing`() {
        assertNull(fitCamera(emptyList(), portrait, 1f))
    }

    @Test
    fun `one point is the centre as close as the map goes`() {
        val fit = assertNotNull(fitCamera(listOf(london), portrait, 1f))
        assertEquals(london.lat, fit.center.lat, absoluteTolerance = 1e-9)
        assertEquals(london.lon, fit.center.lon, absoluteTolerance = 1e-9)
        assertEquals(ZOOM_LIMITS.endInclusive, fit.zoom)
    }

    @Test
    fun `points on top of each other are one point`() {
        val fit = assertNotNull(fitCamera(listOf(london, london, london), portrait, 1f))
        assertEquals(ZOOM_LIMITS.endInclusive, fit.zoom)
    }

    @Test
    fun `maxZoom holds a tight fit back`() {
        val fit = assertNotNull(fitCamera(listOf(london), portrait, 1f, maxZoom = 15f))
        assertEquals(15f, fit.zoom)
        val two = assertNotNull(fitCamera(listOf(london, LatLon(51.5001, -0.12)), portrait, 1f, maxZoom = 12f))
        assertEquals(12f, two.zoom)
    }

    @Test
    fun `points the map cannot zoom out far enough to hold get the widest view it has`() {
        val apart = listOf(LatLon(0.0, -120.0), LatLon(0.0, 120.0), LatLon(60.0, 60.0))
        val fit = assertNotNull(fitCamera(apart, Size(100f, 100f), 1f))
        assertEquals(ZOOM_LIMITS.start, fit.zoom)
    }

    @Test
    fun `the zoom never leaves the limits`() {
        val world = listOf(LatLon(-80.0, -179.9), LatLon(80.0, 179.9), LatLon(0.0, 0.0), LatLon(0.0, 90.0))
        val fit = assertNotNull(fitCamera(world, Size(100f, 100f), 1f, padding = 40.dp))
        assertTrue(fit.zoom in ZOOM_LIMITS, "${fit.zoom}")
    }

    // --- every point is shown, and the fit is tight --------------------------------------------------

    @Test
    fun `every point lands inside the padding and the fit is as large as it can be`() {
        val sets = listOf(
            listOf(london, paris, berlin),
            listOf(LatLon(35.68, 139.69), LatLon(-33.87, 151.21), LatLon(21.3, -157.86)),
            listOf(LatLon(60.0, 10.0), LatLon(-10.0, 10.5)),
            listOf(LatLon(0.0, 30.0), LatLon(0.001, 30.002))
        )
        for (points in sets) for (size in listOf(portrait, landscape)) {
            for (density in listOf(1f, 2.75f)) for (bearing in listOf(0f, 30f, 90f, 200f, 359f)) {
                val padding = 24.dp
                val paddingPx = padding.value * density
                val fit = assertNotNull(fitCamera(points, size, density, padding, bearing))
                val shown = camera(fit, bearing)
                val where = "points=$points size=$size density=$density bearing=$bearing zoom=${fit.zoom}"

                // All of them are inside, give or take the float the camera keeps — unless the map
                // cannot zoom out far enough to hold them, which the last test below pins.
                if (fit.zoom > ZOOM_LIMITS.start) points.forEach {
                    assertTrue(room(shown, it, size, density, paddingPx) > -0.5f, "outside: $it, $where")
                }
                // One of them is on the edge of the padded box: a tighter zoom would lose it.
                if (fit.zoom > ZOOM_LIMITS.start && fit.zoom < ZOOM_LIMITS.endInclusive) {
                    val tightest = points.minOf { room(shown, it, size, density, paddingPx) }
                    assertTrue(abs(tightest) < 1.5f, "$tightest px to spare, $where")
                }
            }
        }
    }

    @Test
    fun `the middle of the points is the middle of the screen`() {
        val points = listOf(london, berlin, LatLon(40.4, -3.7), LatLon(59.9, 10.75))
        for (bearing in listOf(0f, 45f, 135f)) {
            val fit = assertNotNull(fitCamera(points, portrait, 1f, bearing = bearing))
            val shown = camera(fit, bearing)
            val xs = points.map { shown.screenPosition(it, 400f, 800f, 1f).x }
            val ys = points.map { shown.screenPosition(it, 400f, 800f, 1f).y }
            // The extent is centred on the screen on both axes, whichever way it is turned.
            assertEquals(200f, (xs.min() + xs.max()) / 2, absoluteTolerance = 1f, "bearing $bearing")
            assertEquals(400f, (ys.min() + ys.max()) / 2, absoluteTolerance = 1f, "bearing $bearing")
        }
    }

    @Test
    fun `padding takes room from every side`() {
        val points = listOf(london, berlin)
        val bare = assertNotNull(fitCamera(points, portrait, 1f)).zoom
        val padded = assertNotNull(fitCamera(points, portrait, 1f, padding = 50.dp)).zoom
        // 100 of 400 px gone, so a quarter less room: two zoom levels is a factor of four.
        assertTrue(padded < bare, "$padded is not tighter than $bare")
        assertEquals(log2(300f / 400f), padded - bare, absoluteTolerance = 0.05f)
    }

    @Test
    fun `padding is in dp so a denser screen has more pixels of it`() {
        val points = listOf(london, berlin)
        val thin = assertNotNull(fitCamera(points, Size(800f, 1600f), 2f, padding = 50.dp)).zoom
        val same = assertNotNull(fitCamera(points, Size(400f, 800f), 1f, padding = 50.dp)).zoom
        // Twice the pixels and twice the density is the same map.
        assertEquals(same, thin, absoluteTolerance = 1e-4f)
    }

    // --- the bearing -----------------------------------------------------------------------------------

    @Test
    fun `a line run up a portrait screen fits twice as large as the same line run across it`() {
        val eastWest = listOf(LatLon(0.0, 10.0), LatLon(0.0, 14.0))
        val across = assertNotNull(fitCamera(eastWest, portrait, 1f, bearing = 0f)).zoom
        // East at the top: the line now runs up the 800 px side rather than across the 400.
        val up = assertNotNull(fitCamera(eastWest, portrait, 1f, bearing = 90f)).zoom
        assertEquals(1f, up - across, absoluteTolerance = 0.02f)
    }

    // --- the date line -------------------------------------------------------------------------------

    @Test
    fun `points either side of the date line are framed across it`() {
        val fit = assertNotNull(fitCamera(listOf(LatLon(0.0, 179.5), LatLon(0.0, -179.5)), portrait, 1f))
        // The middle is the date line, not the far side of the world, and the view is tight:
        // a degree of longitude across 400 px is zoom 8.1 or so, not the world at zoom 0.
        assertTrue(abs(abs(fit.center.lon) - 180.0) < 1e-6, "centre at ${fit.center.lon}")
        assertTrue(fit.zoom > 8f, "zoom ${fit.zoom}")
    }

    @Test
    fun `the date line is crossed the short way whichever side the points are listed from`() {
        val east = LatLon(10.0, 170.0)
        val west = LatLon(-10.0, -160.0)
        val a = assertNotNull(fitCamera(listOf(east, west), portrait, 1f))
        val b = assertNotNull(fitCamera(listOf(west, east), portrait, 1f))
        assertEquals(a.zoom, b.zoom, absoluteTolerance = 1e-5f)
        assertEquals(a.center.lon, b.center.lon, absoluteTolerance = 1e-9)
        // 30 degrees apart across the Pacific: the middle is at -175, not 5.
        assertEquals(-175.0, a.center.lon, absoluteTolerance = 1e-6)
    }

    @Test
    fun `points across the date line are shown by the camera that frames them`() {
        val points = listOf(LatLon(10.0, 170.0), LatLon(-10.0, -160.0), LatLon(0.0, 179.0))
        val fit = assertNotNull(fitCamera(points, landscape, 1f, padding = 8.dp, bearing = 20f))
        val shown = camera(fit, 20f)
        points.forEach { assertTrue(shown.isOnScreen(it, 1000f, 300f, 1f), "$it is off screen") }
    }

    // --- isOnScreen and screenPosition -----------------------------------------------------------------------

    @Test
    fun `the centre of the camera is the middle of the screen`() {
        val camera = MapCameraState(london, 12f)
        val on = camera.screenPosition(london, 400f, 800f, 1f)
        assertEquals(200f, on.x, absoluteTolerance = 1e-3f)
        assertEquals(400f, on.y, absoluteTolerance = 1e-3f)
    }

    @Test
    fun `a point is on screen inside the map and off it outside`() {
        val camera = MapCameraState(london, 12f)
        assertTrue(camera.isOnScreen(london, 400f, 800f, 1f))
        assertFalse(camera.isOnScreen(paris, 400f, 800f, 1f))
        assertFalse(camera.isOnScreen(LatLon(51.5, 0.5), 400f, 800f, 1f))
    }

    @Test
    fun `a margin widens the screen and a negative margin narrows it`() {
        val camera = MapCameraState(london, 12f)
        // 12 px past the right edge of a 400 px map: 212 px east of the middle.
        val east = LatLon(london.lat, london.lon + degreesPerPx(camera, 212f))
        assertFalse(camera.isOnScreen(east, 400f, 800f, 1f))
        assertTrue(camera.isOnScreen(east, 400f, 800f, 1f, margin = 20f))
        assertFalse(camera.isOnScreen(london, 400f, 800f, 1f, margin = -300f))
    }

    @Test
    fun `the world repeats so a point is placed on the copy nearest the camera`() {
        val camera = MapCameraState(LatLon(0.0, 179.9), 6f)
        val on = camera.screenPosition(LatLon(0.0, -179.9), 400f, 800f, 1f)
        // 0.2 degrees east of the camera across the date line, not 359.8 degrees west.
        assertTrue(on.x > 200f && on.x < 260f, "x = ${on.x}")
    }

    @Test
    fun `the bearing turns the screen position`() {
        val camera = MapCameraState(LatLon(0.0, 0.0), 6f, bearing = 90f)
        // North of the centre, with east at the top: north is to the left of the middle.
        val on = camera.screenPosition(LatLon(1.0, 0.0), 400f, 800f, 1f)
        assertTrue(on.x < 200f, "x = ${on.x}")
        assertEquals(400f, on.y, absoluteTolerance = 0.5f)
    }

    /** Degrees of longitude that [px] pixels span at this camera's zoom, at 1x. */
    private fun degreesPerPx(camera: MapCameraState, px: Float): Double =
        px * 360.0 / (256.0 * 2.0.pow(camera.zoom.toDouble()))
}
