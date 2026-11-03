package com.coderwise.libs.mapview.tiles.vector

import kotlin.test.Test
import kotlin.test.assertEquals

class LabelAngleTest {

    private fun place(turn: Float = 0f) = Label(0f, 0f, "Berlin", 12f, turn = turn)

    private fun road(turn: Float) =
        Label(0f, 0f, "Unter den Linden", 10f, turn = turn, room = 0.5f, upright = false)

    /** What the label ends up looking like on screen, inside a layer turned by -bearing. */
    private fun onScreen(label: Label, bearing: Float) =
        (label.angle(bearing) - bearing + 180f).mod(360f) - 180f

    @Test
    fun `a place name is level whichever way the map is turned`() {
        for (bearing in listOf(0f, 45f, 90f, 175f, 270f, 359f)) {
            assertEquals(0f, onScreen(place(), bearing), 1e-3f, "at $bearing")
        }
    }

    @Test
    fun `a road name lies along its road`() {
        // The road turns with the ground and so does its name: on screen it lies at the road's
        // angle less the bearing, which is where the road is too.
        assertEquals(30f, onScreen(road(30f), 0f), 1e-3f)
        assertEquals(10f, onScreen(road(30f), 20f), 1e-3f)
    }

    @Test
    fun `a road name turns end for end rather than read upside down`() {
        // Turning the map past a right angle would leave this name mirrored; it flips instead.
        assertEquals(-70f, onScreen(road(20f), 90f), 1e-3f)
        assertEquals(80f, onScreen(road(-10f), 90f), 1e-3f)
        // Either way round it reads left to right: never past a quarter turn from level.
        for (bearing in 0..359 step 7) {
            for (turn in listOf(-89f, -45f, 0f, 45f, 90f)) {
                val read = onScreen(road(turn), bearing.toFloat())
                assertEquals(true, read > -90f && read <= 90f, "turn $turn at $bearing was $read")
            }
        }
    }
}
