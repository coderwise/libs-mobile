package com.coderwise.libs.mapview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What [MapOverlayScope.trace] puts into a path. A [Path] is only real on a host with graphics
 * behind it, so these run on the desktop target rather than in common.
 */
class MapTraceTest {

    private val london = LatLon(51.5074, -0.1278)

    private fun overlay(center: LatLon = london, zoom: Float = 12f): MapOverlayScope =
        MapOverlayState(MapCameraState(center, zoom)).apply { measured(1080f, 1920f, 1f) }

    private fun length(path: Path) = PathMeasure().apply { setPath(path, false) }.length

    @Test
    fun `a traced shape lies where its points are projected`() {
        val overlay = overlay()
        val points = listOf(
            LatLon(51.50, -0.14), LatLon(51.52, -0.12), LatLon(51.51, -0.10), LatLon(51.49, -0.13)
        )
        val path = Path().also { overlay.trace(it, points.toWorld()) }

        val on = points.map { overlay.project(it) }
        val bounds = path.getBounds()
        assertEquals(on.minOf { it.x }, bounds.left, 1e-2f)
        assertEquals(on.maxOf { it.x }, bounds.right, 1e-2f)
        assertEquals(on.minOf { it.y }, bounds.top, 1e-2f)
        assertEquals(on.maxOf { it.y }, bounds.bottom, 1e-2f)
    }

    @Test
    fun `a shape across the antimeridian is traced the short way round`() {
        val overlay = overlay(center = LatLon(-18.0, 180.0), zoom = 6f)
        val path = Path().also {
            overlay.trace(it, listOf(LatLon(-17.0, 179.0), LatLon(-18.0, -179.0), LatLon(-19.0, 179.5)).toWorld())
        }

        // Two degrees of longitude at zoom 6 is a few hundred pixels; the long way round is the
        // whole world, tens of thousands.
        val bounds = path.getBounds()
        assertTrue(bounds.width < 1000f, "expected a short shape, was ${bounds.width} wide")
    }

    @Test
    fun `every call starts a subpath of its own`() {
        val overlay = overlay()
        val west = listOf(LatLon(51.50, -0.20), LatLon(51.50, -0.19)).toWorld()
        val east = listOf(LatLon(51.50, -0.05), LatLon(51.50, -0.04)).toWorld()

        val alone = Path().also { overlay.trace(it, west) }
        val together = Path().also {
            overlay.trace(it, west)
            overlay.trace(it, east)
        }

        // A path measures its first contour. Without its own moveTo the second shape would be
        // joined on to the first, and the gap between them would be stroked as well.
        assertEquals(length(alone), length(together), 1e-2f)
        assertTrue(together.getBounds().right > alone.getBounds().right, "the second shape was not traced")
    }

    @Test
    fun `fewer than two points trace nothing`() {
        val overlay = overlay()
        val path = Path()
        overlay.trace(path, emptyList<LatLon>().toWorld())
        overlay.trace(path, listOf(london).toWorld())
        assertTrue(path.isEmpty, "a lone point has no line to draw")
    }

    @Test
    fun `tracing follows the camera`() {
        val camera = MapCameraState(london, 12f)
        val overlay = MapOverlayState(camera).apply { measured(1080f, 1920f, 1f) }
        val world = listOf(london, LatLon(51.51, -0.11)).toWorld()

        val before = Path().also { overlay.trace(it, world) }.getBounds()
        camera.moveTo(LatLon(51.5174, -0.1278))
        val after = Path().also { overlay.trace(it, world) }.getBounds()

        // The camera went north, so the same shape is further down the screen.
        assertTrue(after.top > before.top + 1f, "expected the shape to move down, was ${before.top} then ${after.top}")
        assertEquals(before.left, after.left, 1e-2f)
        assertEquals(Offset(before.width, before.height), Offset(after.width, after.height))
    }
}
