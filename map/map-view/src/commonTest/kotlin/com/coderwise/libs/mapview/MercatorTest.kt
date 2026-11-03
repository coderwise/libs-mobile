package com.coderwise.libs.mapview

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MercatorTest {

    @Test
    fun `null island sits at the centre of the world square`() {
        assertEquals(0.5, Mercator.x(0.0), 1e-9)
        assertEquals(0.5, Mercator.y(0.0), 1e-9)
    }

    @Test
    fun `latitude is clamped to the mercator limit`() {
        assertTrue(abs(Mercator.y(89.9)) < 1e-9, "the pole should land on the top edge")
    }

    @Test
    fun `north is up and east is right`() {
        assertTrue(Mercator.y(1.0) < Mercator.y(0.0))
        assertTrue(Mercator.x(1.0) > Mercator.x(0.0))
    }

    @Test
    fun `geography round trips through the unit square`() {
        val here = LatLon(51.5074, -0.1278)
        assertEquals(here.lat, Mercator.lat(Mercator.y(here.lat)), 1e-9)
        assertEquals(here.lon, Mercator.lon(Mercator.x(here.lon)), 1e-9)
    }

    @Test
    fun `the world doubles in pixels with every zoom level`() {
        assertEquals(256.0, Mercator.worldPixels(0f, 1f), 1e-9)
        assertEquals(512.0, Mercator.worldPixels(1f, 1f), 1e-9)
        assertEquals(512.0, Mercator.worldPixels(0f, 2f), 1e-9)
    }
}
