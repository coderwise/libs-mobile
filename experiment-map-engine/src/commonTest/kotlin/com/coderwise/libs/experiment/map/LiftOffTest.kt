package com.coderwise.libs.experiment.map

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class LiftOffTest {

    @Test
    fun `nothing to measure across reads zero`() {
        val liftOff = LiftOff()
        assertEquals(Offset.Zero, liftOff.velocity())
        liftOff.add(0, Offset(10f, 10f))
        assertEquals(Offset.Zero, liftOff.velocity())
    }

    @Test
    fun `velocity is displacement over elapsed time`() {
        val liftOff = LiftOff()
        liftOff.add(0, Offset.Zero)
        liftOff.add(100, Offset(-100f, 50f))
        assertEquals(Offset(-1000f, 500f), liftOff.velocity())
    }

    /** The regression: a stalled frame leaves only a couple of moves to measure across. */
    @Test
    fun `two moves are enough to measure a throw`() {
        val liftOff = LiftOff()
        liftOff.add(552, Offset(852f, 1632f))
        liftOff.add(605, Offset(669f, 1370f))
        val velocity = liftOff.velocity()
        assertEquals(-3452f, velocity.x, 1f)
        assertEquals(-4943f, velocity.y, 1f)
    }

    @Test
    fun `a pause longer than the window starts the measurement over`() {
        val liftOff = LiftOff()
        liftOff.add(0, Offset(500f, 900f))
        liftOff.add(50, Offset(500f, 400f)) // moving fast, then the pointer stopped
        liftOff.add(400, Offset(500f, 380f))
        assertEquals(Offset.Zero, liftOff.velocity())
    }

    @Test
    fun `a finger resting on glass reads as stopped`() {
        val liftOff = LiftOff()
        liftOff.add(0, Offset(500f, 900f))
        liftOff.add(50, Offset(500f, 400f))
        liftOff.add(120, Offset(501f, 400f)) // the jitter of a finger resting on glass
        liftOff.add(200, Offset(500f, 401f))
        assertEquals(6.7f, liftOff.velocity().y, 0.1f) // 1 px over 150 ms: nothing to throw
    }

    @Test
    fun `only the tail of a long drag counts`() {
        val liftOff = LiftOff()
        liftOff.add(0, Offset.Zero)
        liftOff.add(500, Offset(0f, -5000f)) // a fast stretch, long before the lift
        liftOff.add(560, Offset(0f, -5010f))
        liftOff.add(620, Offset(0f, -5020f)) // ambling by the time the finger left
        // 20 px over the 120 ms that is left, not the 5020 px of the whole drag.
        assertEquals(0f, liftOff.velocity().x)
        assertEquals(-166.7f, liftOff.velocity().y, 0.1f)
    }

    @Test
    fun `a second finger starts the measurement over`() {
        val liftOff = LiftOff()
        liftOff.add(0, Offset.Zero)
        liftOff.add(100, Offset(-100f, 50f))
        liftOff.reset()
        assertEquals(Offset.Zero, liftOff.velocity())
    }
}
