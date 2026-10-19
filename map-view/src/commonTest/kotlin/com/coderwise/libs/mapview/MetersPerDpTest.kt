package com.coderwise.libs.mapview

import kotlin.test.Test
import kotlin.test.assertEquals

class MetersPerDpTest {

    @Test
    fun `at the widest zoom on the equator the whole equator is 512 dp`() {
        // A tile is 256 dp and zoom 1 is two of them across: 40 075 km over 512 dp.
        assertEquals(78_271.52, MapCameraState(LatLon(0.0, 0.0), zoom = 1f).metersPerDp, 1e-2)
    }

    @Test
    fun `every zoom level halves it`() {
        val z10 = MapCameraState(LatLon(0.0, 0.0), zoom = 10f).metersPerDp
        val z11 = MapCameraState(LatLon(0.0, 0.0), zoom = 11f).metersPerDp
        assertEquals(z10 / 2, z11, 1e-9)
    }

    @Test
    fun `a metre is more dp towards the pole which is the scale of the latitude asked for`() {
        // cos 60 degrees is a half.
        val equator = MapCameraState(LatLon(0.0, 0.0), zoom = 12f).metersPerDp
        val sixty = MapCameraState(LatLon(60.0, 0.0), zoom = 12f).metersPerDp
        assertEquals(equator / 2, sixty, equator * 1e-6)
    }

    @Test
    fun `it follows the camera as it moves`() {
        val camera = MapCameraState(LatLon(0.0, 0.0), zoom = 5f)
        val before = camera.metersPerDp
        camera.moveTo(LatLon(0.0, 0.0), zoom = 6f)
        assertEquals(before / 2, camera.metersPerDp, 1e-9)
    }
}
