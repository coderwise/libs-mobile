package com.coderwise.libs.mapcore

import kotlin.test.Test
import kotlin.test.assertEquals

class MapMathTest {

    @Test
    fun `latLonToTile London Zoom10`() {
        val lat = 51.5074
        val lon = -0.1278
        val zoom = 10
        val expectedX = 511
        val expectedY = 340

        val (x, y) = MapMath.latLonToTile(lat, lon, zoom)

        assertEquals(expectedX, x)
        assertEquals(expectedY, y)
    }

    @Test
    fun `latLonToTile Equator PrimeMeridian Zoom0`() {
        val lat = 0.0
        val lon = 0.0
        val zoom = 0
        val expectedX = 0
        val expectedY = 0

        val (x, y) = MapMath.latLonToTile(lat, lon, zoom)

        assertEquals(expectedX, x)
        assertEquals(expectedY, y)
    }

    @Test
    fun `latLonToTileFractional London Zoom10`() {
        val lat = 51.5074
        val lon = -0.1278
        val zoom = 10.0
        
        val (x, y) = MapMath.latLonToTileFractional(lat, lon, zoom)

        assertEquals(511.6361, x, 0.001)
        assertEquals(340.5061, y, 0.001)
    }

    @Test
    fun `latLonToTileFractional London Zoom10_5`() {
        val lat = 51.5074
        val lon = -0.1278
        val zoom = 10.5
        
        val (x, y) = MapMath.latLonToTileFractional(lat, lon, zoom)

        // 2^10.5 is approx 1.414 * 2^10
        assertEquals(723.5629, x, 0.001)
        assertEquals(481.5484, y, 0.001)
    }

    @Test
    fun `tileToLatLon London Zoom10`() {
        val x = 511.637
        val y = 340.505
        val zoom = 10.0

        val (lat, lon) = MapMath.tileToLatLon(x, y, zoom)

        assertEquals(51.5074, lat, 0.001)
        assertEquals(-0.1278, lon, 0.001)
    }

    @Test
    fun `tileToLatLon London Zoom10_5`() {
        val x = 723.564
        val y = 481.547
        val zoom = 10.5

        val (lat, lon) = MapMath.tileToLatLon(x, y, zoom)

        assertEquals(51.5074, lat, 0.001)
        assertEquals(-0.1278, lon, 0.001)
    }

    @Test
    fun `roundTrip Equator PrimeMeridian`() {
        val lat = 0.0
        val lon = 0.0
        val zoom = 10.0

        val (x, y) = MapMath.latLonToTileFractional(lat, lon, zoom)
        val (newLat, newLon) = MapMath.tileToLatLon(x, y, zoom)

        assertEquals(lat, newLat, 0.000001)
        assertEquals(lon, newLon, 0.000001)
    }

    @Test
    fun `roundTrip FractionalZoom`() {
        val lat = 45.0
        val lon = 90.0
        val zoom = 12.34

        val (x, y) = MapMath.latLonToTileFractional(lat, lon, zoom)
        val (newLat, newLon) = MapMath.tileToLatLon(x, y, zoom)

        assertEquals(lat, newLat, 0.000001)
        assertEquals(lon, newLon, 0.000001)
    }

    @Test
    fun `roundTrip GranularZoom 5_6`() {
        val lat = 51.5074
        val lon = -0.1278
        val zoom = 5.6

        val (x, y) = MapMath.latLonToTileFractional(lat, lon, zoom)
        val (newLat, newLon) = MapMath.tileToLatLon(x, y, zoom)

        assertEquals(lat, newLat, 0.000001)
        assertEquals(lon, newLon, 0.000001)
    }

    @Test
    fun `roundTrip GranularZoom 5_9`() {
        val lat = 51.5074
        val lon = -0.1278
        val zoom = 5.9

        val (x, y) = MapMath.latLonToTileFractional(lat, lon, zoom)
        val (newLat, newLon) = MapMath.tileToLatLon(x, y, zoom)

        assertEquals(lat, newLat, 0.000001)
        assertEquals(lon, newLon, 0.000001)
    }
}
