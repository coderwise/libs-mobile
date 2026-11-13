package com.coderwise.libs.mapview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZoomRangeTest {

    private val london = LatLon(51.5074, -0.1278)

    @Test
    fun `a camera goes as far as ZOOM_LIMITS until it is told otherwise`() {
        val camera = MapCameraState(london, zoom = 5f)

        assertEquals(ZOOM_LIMITS, camera.zoomRange)
        camera.moveTo(london, 30f)
        assertEquals(ZOOM_LIMITS.endInclusive, camera.zoom)
        camera.moveTo(london, -4f)
        assertEquals(ZOOM_LIMITS.start, camera.zoom)
    }

    @Test
    fun `moveTo stops at either end of the range`() {
        val camera = MapCameraState(london, zoom = 10f).apply { zoomRange = 3f..18f }

        camera.moveTo(london, 20f)
        assertEquals(18f, camera.zoom)
        camera.moveTo(london, 1f)
        assertEquals(3f, camera.zoom)
    }

    @Test
    fun `a pinch stops at the end of the range`() {
        val camera = MapCameraState(london, zoom = 17f).apply { zoomRange = 1f..18f }

        // A factor of eight is three levels.
        camera.zoomBy(8f, 500f, 500f, 1000f, 1000f, 1f)
        assertEquals(18f, camera.zoom)
    }

    @Test
    fun `narrowing the range takes a camera outside it back in`() {
        val deep = MapCameraState(london, zoom = 20f).apply { zoomRange = 1f..18f }
        val shallow = MapCameraState(london, zoom = 2f).apply { zoomRange = 5f..18f }

        assertEquals(18f, deep.zoom)
        assertEquals(5f, shallow.zoom)
    }

    @Test
    fun `widening the range leaves the zoom where it is`() {
        val camera = MapCameraState(london, zoom = 18f).apply { zoomRange = 1f..18f }

        camera.zoomRange = ZOOM_LIMITS
        assertEquals(18f, camera.zoom)
        camera.moveTo(london, 20f)
        assertEquals(20f, camera.zoom)
    }

    @Test
    fun `a range wider than ZOOM_LIMITS is held to them`() {
        val camera = MapCameraState(london, zoom = 5f).apply { zoomRange = 0f..25f }

        assertEquals(ZOOM_LIMITS, camera.zoomRange)
    }

    @Test
    fun `an empty range is refused`() {
        val camera = MapCameraState(london, zoom = 5f)

        assertFailsWith<IllegalArgumentException> { camera.zoomRange = 10f..5f }
        assertEquals(ZOOM_LIMITS, camera.zoomRange)
    }
}
