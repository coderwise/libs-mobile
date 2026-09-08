package com.coderwise.libs.experiment.map

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class FlingTest {

    // Thresholds are dp per second; MapGestures scales them by the screen's density.

    @Test
    fun `a slow lift-off is a drag rather than a throw`() {
        assertNull(flingVelocity(10f, 8f))
    }

    @Test
    fun `a fast lift-off flings`() {
        assertNotNull(flingVelocity(0f, -1500f))
    }

    @Test
    fun `the threshold is on combined speed rather than either axis`() {
        assertNull(flingVelocity(12f, 12f))       // 17 dp/s combined, still a drag
        assertNotNull(flingVelocity(16f, 16f))    // 23 dp/s, a throw
    }

    @Test
    fun `clamping keeps direction and caps speed`() {
        val thrown = flingVelocity(30_000f, 40_000f, max = 5_000f)!!
        assertEquals(5_000f, hypot(thrown.x, thrown.y), 1e-2f)
        assertEquals(30f / 40f, thrown.x / thrown.y, 1e-5f)
    }

    @Test
    fun `velocities under the cap are left alone`() {
        assertEquals(300f, flingVelocity(300f, -400f)!!.x)
    }
}
