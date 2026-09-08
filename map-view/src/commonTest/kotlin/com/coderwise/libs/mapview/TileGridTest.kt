package com.coderwise.libs.mapview

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileGridTest {

    private fun grid(
        zoom: Float,
        width: Float = 1080f,
        height: Float = 1920f,
        center: LatLon = LatLon(0.0, 0.0),
        density: Float = 1f
    ) = tileGrid(
        camera = MapCameraState(center, zoom),
        width = width,
        height = height,
        density = density,
        state = MapState<Unit>(zoomRange = 0..19)
    )

    @Test
    fun `zoom 1 over null island wants the four tiles that meet there`() {
        val window = grid(zoom = 1f, width = 256f, height = 256f).window
        assertEquals(
            setOf(TileKey(1, 0, 0), TileKey(1, 1, 0), TileKey(1, 0, 1), TileKey(1, 1, 1)),
            window.keys.toSet()
        )
    }

    @Test
    fun `a tile is 256 dp wide whatever the screen`() {
        for (density in listOf(1f, 2f, 2.625f, 3f)) {
            val grid = grid(zoom = 11f, density = density)
            assertEquals((256 * density).toDouble(), grid.side, 1e-2, "density $density")
        }
    }

    @Test
    fun `a fractional zoom scales the tile instead of changing level`() {
        val grid = grid(zoom = 10.5f)
        assertEquals(10, grid.z)
        assertEquals(256.0 * sqrt(2.0), grid.side, 1e-3)
    }

    @Test
    fun `the whole part of the zoom is the level read`() {
        assertEquals(11, grid(zoom = 11f).z)
    }

    @Test
    fun `density does not change which level is read`() {
        assertEquals(11, grid(zoom = 11f, density = 1f).z)
        assertEquals(11, grid(zoom = 11f, density = 3f).z)
    }

    @Test
    fun `the window covers the viewport`() {
        val grid = grid(zoom = 12f)
        assertTrue(grid.columns.first * grid.side <= grid.originX, "left edge uncovered")
        assertTrue((grid.columns.last + 1) * grid.side >= grid.originX + 1080f, "right edge uncovered")
        assertTrue((grid.rows.last + 1) * grid.side >= grid.originY + 1920f, "bottom edge uncovered")
    }

    @Test
    fun `nothing is asked for above or below the poles`() {
        assertTrue(grid(zoom = 2f, width = 2000f, height = 2000f).window.keys.all { it.y in 0..3 })
    }

    @Test
    fun `tile x wraps across the antimeridian`() {
        val window = grid(zoom = 2f, width = 2000f, height = 800f, center = LatLon(0.0, 179.0)).window
        assertTrue(window.keys.all { it.x in 0..3 }, window.keys.map { it.x }.toString())
    }
}
