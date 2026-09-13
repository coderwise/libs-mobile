package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [couldFit] is what keeps a name off the text layout it would only fail: it has to rule out
 * everything that cannot fit, and rule out nothing that can.
 */
class LabelFitTest {

    private val density = Density(1f)

    /** A road name and the straight run it has to sit on, as a fraction of its tile. */
    private fun road(text: String, size: Float = 12f, room: Float) =
        Label(x = 0.5f, y = 0.5f, text = text, size = size, turn = 0f, room = room, upright = false)

    @Test
    fun `a place is never turned away for want of room`() {
        // Nothing says where a place name may not reach, so it has all the room there is.
        val place = Label(x = 0.5f, y = 0.5f, text = "Llanfairpwllgwyngyll", size = 16f)
        assertTrue(density.couldFit(place, side = 1f))
    }

    @Test
    fun `a name with room to spare is measured`() {
        assertTrue(density.couldFit(road("Main Street", room = 0.5f), side = 256f))
    }

    @Test
    fun `a name wider than its run is dropped before it is laid out`() {
        // 11 letters at 12 px need at least 26.4 px; a twentieth of a 256 px tile is 12.8.
        assertFalse(density.couldFit(road("Main Street", room = 0.05f), side = 256f))
    }

    @Test
    fun `the same run on a stretched tile admits the name it refused`() {
        // The point of asking again per slot: an over-zoomed ancestor draws its tile four times as
        // wide as its own level wanted, and the run under the name grows with it.
        val label = road("Main Street", room = 0.05f)
        assertFalse(density.couldFit(label, side = 256f))
        assertTrue(density.couldFit(label, side = 1024f))
    }

    @Test
    fun `bigger type needs more road`() {
        val room = 0.1f
        assertTrue(density.couldFit(road("Main Street", size = 10f, room = room), side = 256f))
        assertFalse(density.couldFit(road("Main Street", size = 24f, room = room), side = 256f))
    }
}
