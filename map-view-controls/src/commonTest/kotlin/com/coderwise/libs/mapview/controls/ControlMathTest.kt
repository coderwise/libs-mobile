package com.coderwise.libs.mapview.controls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlMathTest {

    // --- shortestTurn --------------------------------------------------------------------------

    @Test
    fun `a turn is the short way round`() {
        assertEquals(20f, shortestTurn(from = 350f, to = 10f), 1e-4f)
        assertEquals(-20f, shortestTurn(from = 10f, to = 350f), 1e-4f)
        assertEquals(90f, shortestTurn(from = 0f, to = 90f), 1e-4f)
        assertEquals(-90f, shortestTurn(from = 90f, to = 0f), 1e-4f)
    }

    @Test
    fun `no turn is none`() {
        assertEquals(0f, shortestTurn(from = 123f, to = 123f), 1e-4f)
    }

    // --- scale bar -----------------------------------------------------------------------------

    @Test
    fun `a metric bar is a round number of metres no wider than the room`() {
        // 4.78 m a dp and 120 dp of room is 573 m; the round number under that is 500.
        val bar = assertNotNull(scaleBarSize(4.7773, 120f, ScaleUnits.Metric))
        assertEquals("500 m", bar.label)
        assertEquals(500f / 4.7773f, bar.width, 1e-2f)
        assertTrue(bar.width <= 120f)
    }

    @Test
    fun `metres give way to kilometres at a thousand`() {
        assertEquals("1 km", scaleBarSize(9.0, 120f, ScaleUnits.Metric)?.label) // 1080 m of room
        assertEquals("500 m", scaleBarSize(8.0, 120f, ScaleUnits.Metric)?.label) // 960 m
        assertEquals("200 km", scaleBarSize(2000.0, 120f, ScaleUnits.Metric)?.label) // 240 km
    }

    @Test
    fun `an imperial bar counts feet until a mile and then miles`() {
        // 573 m is 1880 ft.
        assertEquals("1000 ft", scaleBarSize(4.7773, 120f, ScaleUnits.Imperial)?.label)
        // 20 m a dp over 120 dp is 2400 m, a mile and a half.
        assertEquals("1 mi", scaleBarSize(20.0, 120f, ScaleUnits.Imperial)?.label)
        assertEquals("5 mi", scaleBarSize(80.0, 120f, ScaleUnits.Imperial)?.label) // 6 miles
    }

    @Test
    fun `the bar is the length it says and not the length of the room`() {
        // 1 mile is 1609.344 m.
        val bar = assertNotNull(scaleBarSize(20.0, 120f, ScaleUnits.Imperial))
        assertEquals(1609.344f / 20f, bar.width, 1e-2f)
    }

    @Test
    fun `zoomed far in a bar can be a fraction of a metre`() {
        assertEquals("0.5 m", scaleBarSize(0.005, 120f, ScaleUnits.Metric)?.label) // 0.6 m of room
    }

    @Test
    fun `nothing sensible to draw is no bar`() {
        assertNull(scaleBarSize(0.0, 120f, ScaleUnits.Metric))
        assertNull(scaleBarSize(Double.NaN, 120f, ScaleUnits.Metric))
        assertNull(scaleBarSize(Double.POSITIVE_INFINITY, 120f, ScaleUnits.Metric))
        assertNull(scaleBarSize(5.0, 0f, ScaleUnits.Metric))
    }

    @Test
    fun `round numbers are one or two or five of a power of ten`() {
        assertEquals(1.0, niceFloor(1.0), 0.0)
        assertEquals(1.0, niceFloor(1.9), 0.0)
        assertEquals(2.0, niceFloor(2.0), 0.0)
        assertEquals(2.0, niceFloor(4.99), 0.0)
        assertEquals(5.0, niceFloor(5.0), 0.0)
        assertEquals(5.0, niceFloor(9.99), 0.0)
        assertEquals(10.0, niceFloor(10.0), 0.0)
        assertEquals(500.0, niceFloor(573.3), 0.0)
        assertEquals(0.05, niceFloor(0.07), 1e-12)
    }

    // --- compass tape --------------------------------------------------------------------------

    @Test
    fun `a strip facing north has north in the middle`() {
        val ticks = compassTicks(bearing = 0f, span = 120f)
        val north = ticks.single { it.offset == 0f }
        assertEquals(0, north.heading)
        assertEquals("N", north.label)
        assertEquals(TickKind.Cardinal, north.kind)
    }

    @Test
    fun `a strip is marked every five degrees and runs to its edges`() {
        val ticks = compassTicks(bearing = 0f, span = 120f)
        assertEquals(25, ticks.size) // -60 to 60
        assertEquals(-0.5f, ticks.first().offset, 1e-6f)
        assertEquals(0.5f, ticks.last().offset, 1e-6f)
        assertEquals(300, ticks.first().heading) // 60 degrees west of north
    }

    @Test
    fun `the points of the compass are named and thirty degrees are numbered`() {
        val labels = compassTicks(bearing = 0f, span = 120f).associate { it.heading to it.label }
        assertEquals("NE", labels[45])
        assertEquals("NW", labels[315])
        assertEquals("30", labels[30])
        assertEquals("330", labels[330])
        assertNull(labels[5])
        assertNull(labels[15])
    }

    @Test
    fun `a strip slides as the map turns`() {
        // Facing east, north is 90 degrees to the left, which is off a strip 120 wide.
        val east = compassTicks(bearing = 90f, span = 120f)
        assertTrue(east.none { it.heading == 0 })
        assertEquals(0f, east.single { it.heading == 90 }.offset, 1e-6f)
        assertEquals("E", east.single { it.heading == 90 }.label)
    }

    @Test
    fun `a strip wraps past north`() {
        val ticks = compassTicks(bearing = 350f, span = 120f)
        val north = ticks.single { it.heading == 0 }
        assertEquals(10f / 120f, north.offset, 1e-6f)
        assertEquals("N", north.label)
    }
}
