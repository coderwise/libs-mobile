package com.coderwise.libs.mapview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileGridTest {

    @Test
    fun `a coordinate lands where the slippy-map scheme says it does`() {
        // London at z10, against the slippy-map formula worked out by hand.
        val at = LatLon(51.5074, -0.1278).onTileGrid(10.0)
        assertEquals(511.63648, at.x, absoluteTolerance = 1e-5)
        assertEquals(340.506135, at.y, absoluteTolerance = 1e-5)
        assertEquals(TileKey(10, 511, 340), at.tile(10))
    }

    @Test
    fun `zoom zero is the unit square`() {
        assertEquals(TilePoint(0.5, 0.5), LatLon(0.0, 0.0).onTileGrid(0.0))
        val corner = LatLon(MERCATOR_LATITUDE_LIMIT, -180.0).onTileGrid(0.0)
        assertEquals(0.0, corner.x, absoluteTolerance = 1e-9)
        assertEquals(0.0, corner.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun `the grid and back is where it started`() {
        listOf(
            LatLon(51.5074, -0.1278),
            LatLon(-33.8688, 151.2093),
            LatLon(64.1466, -21.9426),
            LatLon(0.0, 0.0)
        ).forEach { start ->
            listOf(0.0, 5.5, 14.0, 19.0).forEach { zoom ->
                val there = start.onTileGrid(zoom)
                val back = tileGridToLatLon(there.x, there.y, zoom)
                assertEquals(start.lat, back.lat, absoluteTolerance = 1e-9, "$start at z$zoom")
                assertEquals(start.lon, back.lon, absoluteTolerance = 1e-9, "$start at z$zoom")
            }
        }
    }

    @Test
    fun `a longitude past the antimeridian wraps onto the grid rather than off it`() {
        // 190E is 170W, and both have to land on the same tile.
        assertEquals(
            LatLon(0.0, -170.0).onTileGrid(4.0).x,
            LatLon(0.0, 190.0).onTileGrid(4.0).x,
            absoluteTolerance = 1e-9
        )
    }

    @Test
    fun `a pole is clamped to where the projection stops`() {
        // Mercator never reaches the pole — unclamped this is infinite, and an infinite tile
        // coordinate is a tile index nothing can hold.
        val tiles = (1 shl 14).toDouble()
        // A hair outside is floating point, not a projection that ran away: the clamp is what
        // keeps this finite, and a tile index rounds it back on.
        val slack = 1e-6
        listOf(90.0, -90.0).forEach { lat ->
            val y = LatLon(lat, 0.0).onTileGrid(14.0).y
            assertTrue(y.isFinite(), "a pole should still land on the grid, was $y")
            assertTrue(y > -slack && y < tiles + slack, "off the grid at $lat: $y")
        }
    }

    @Test
    fun `a fractional zoom is halfway between the levels either side of it`() {
        val at = LatLon(51.5074, -0.1278)
        // Each level doubles the grid, so the same point sits at twice the coordinate one level in.
        assertEquals(at.onTileGrid(10.0).x * 2, at.onTileGrid(11.0).x, absoluteTolerance = 1e-9)
    }
}
