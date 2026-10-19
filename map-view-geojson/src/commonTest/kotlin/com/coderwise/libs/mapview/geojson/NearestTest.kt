package com.coderwise.libs.mapview.geojson

import com.coderwise.libs.mapview.LatLon
import com.coderwise.libs.mapview.toWorld
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which feature a touch is nearest, and how far. Distances are asked in degrees of longitude —
 * a world unit is 360 of them — which near the equator is the same on the latitude axis.
 */
class NearestTest {

    private fun shapes(vararg features: Feature) = collection(*features).toShapes()

    private fun assertDegrees(expected: Double, actual: Double?, message: String? = null) {
        assertNotNull(actual, message)
        assertTrue(abs(expected - actual) < 1e-3, "${message ?: ""} expected $expected, was $actual")
    }

    private val box = polygon(ring(-2.0, -2.0, 2.0, 2.0))

    @Test
    fun `inside a polygon is no distance at all`() {
        assertEquals(0.0, shapes(feature(box)).degreesTo(LatLon(0.0, 0.0)))
        assertEquals(0.0, shapes(feature(box)).degreesTo(LatLon(1.9, -1.9)))
    }

    @Test
    fun `outside a polygon is the distance to the nearest edge`() {
        val shapes = shapes(feature(box))
        assertDegrees(1.0, shapes.degreesTo(LatLon(0.0, 3.0)), "east of it")
        assertDegrees(0.5, shapes.degreesTo(LatLon(-2.5, 0.0)), "south of it")
        assertDegrees(0.5, shapes.degreesTo(LatLon(0.0, -2.5)), "west of it")
    }

    @Test
    fun `beyond a corner is the distance to the corner`() {
        // 0.3 east and 0.4 north of the north-east corner: a 3-4-5 triangle, at the equator's scale.
        val corner = shapes(feature(box)).degreesTo(LatLon(2.4, 2.3))
        assertTrue(corner != null && abs(corner - 0.5) < 1e-3, "was $corner")
    }

    @Test
    fun `a hole is not inside`() {
        val shapes = shapes(feature(polygon(ring(-4.0, -4.0, 4.0, 4.0), ring(-1.0, -1.0, 1.0, 1.0))))
        assertEquals(0.0, shapes.degreesTo(LatLon(0.0, 2.5)), "in the ring between the two")
        assertDegrees(0.5, shapes.degreesTo(LatLon(0.0, 0.5)), "in the hole, half a degree from its edge")
        assertDegrees(0.0, shapes.degreesTo(LatLon(-3.9, 3.9)), "near the outside")
    }

    @Test
    fun `an island in a hole is inside again`() {
        val shapes = shapes(
            feature(
                Geometry.MultiPolygon(
                    listOf(
                        listOf(ring(-4.0, -4.0, 4.0, 4.0), ring(-2.0, -2.0, 2.0, 2.0)),
                        listOf(ring(-1.0, -1.0, 1.0, 1.0))
                    )
                )
            )
        )
        assertEquals(0.0, shapes.degreesTo(LatLon(0.0, 0.0)), "on the island")
        assertDegrees(0.5, shapes.degreesTo(LatLon(0.0, 1.5)), "in the water round it")
        assertEquals(0.0, shapes.degreesTo(LatLon(0.0, 3.0)), "on the mainland")
    }

    @Test
    fun `between the polygons of a multi-polygon is outside`() {
        val shapes = shapes(feature(Geometry.MultiPolygon(listOf(listOf(square(-5.0, 0.0, 1.0)), listOf(square(5.0, 0.0, 1.0))))))
        assertEquals(0.0, shapes.degreesTo(LatLon(0.0, -5.0)))
        assertEquals(0.0, shapes.degreesTo(LatLon(0.0, 5.0)))
        assertDegrees(4.0, shapes.degreesTo(LatLon(0.0, 0.0)), "four degrees from either")
    }

    @Test
    fun `a ring that is not closed is still a polygon`() {
        val open = ring(-2.0, -2.0, 2.0, 2.0).dropLast(1)
        assertEquals(0.0, shapes(feature(polygon(open))).degreesTo(LatLon(0.0, 0.0)))
    }

    @Test
    fun `a line is as far as its nearest point`() {
        val shapes = shapes(feature(Geometry.LineString(listOf(LatLon(0.0, -2.0), LatLon(0.0, 2.0)))))
        assertDegrees(0.0, shapes.degreesTo(LatLon(0.0, 1.0)), "on it")
        assertDegrees(0.5, shapes.degreesTo(LatLon(0.5, 0.0)), "beside it")
        assertDegrees(1.0, shapes.degreesTo(LatLon(0.0, 3.0)), "beyond its end")
    }

    @Test
    fun `a line is not a polygon even if it comes back to where it started`() {
        val loop = Geometry.LineString(ring(-2.0, -2.0, 2.0, 2.0))
        assertDegrees(2.0, shapes(feature(loop)).degreesTo(LatLon(0.0, 0.0)), "the middle of a loop is not on it")
    }

    @Test
    fun `a point is as far as it is`() {
        val shapes = shapes(feature(Geometry.MultiPoint(listOf(LatLon(0.0, 0.0), LatLon(0.0, 10.0)))))
        assertDegrees(0.0, shapes.degreesTo(LatLon(0.0, 10.0)))
        assertDegrees(1.0, shapes.degreesTo(LatLon(0.0, 9.0)))
    }

    @Test
    fun `the nearest feature wins`() {
        val shapes = shapes(
            feature(Geometry.Point(LatLon(0.0, 0.0)), id = "origin"),
            feature(Geometry.Point(LatLon(0.0, 10.0)), id = "ten"),
            feature(Geometry.Point(LatLon(0.0, 20.0)), id = "twenty")
        )
        assertEquals("origin", shapes.featureAt(LatLon(0.0, 3.0))?.id)
        assertEquals("ten", shapes.featureAt(LatLon(0.0, 7.0))?.id)
        assertEquals("twenty", shapes.featureAt(LatLon(0.0, 30.0))?.id)
    }

    @Test
    fun `a polygon you are inside beats a line you are beside`() {
        val shapes = shapes(
            feature(polygon(ring(-2.0, -2.0, 2.0, 2.0)), id = "area"),
            feature(Geometry.LineString(listOf(LatLon(3.0, -9.0), LatLon(3.0, 9.0))), id = "road")
        )
        // The road is declared after the polygon, so it is drawn over it: it is the nearest that counts.
        assertEquals("area", shapes.featureAt(LatLon(0.0, 0.0))?.id, "inside the area, far from the road")
        assertEquals("area", shapes.featureAt(LatLon(2.3, 0.0))?.id, "outside the area by 0.3, from the road by 0.7")
        assertEquals("road", shapes.featureAt(LatLon(2.8, 0.0))?.id, "outside the area by 0.8, from the road by 0.2")
    }

    @Test
    fun `where features are equally near the one drawn on top wins`() {
        val shapes = shapes(
            feature(polygon(ring(-2.0, -2.0, 2.0, 2.0)), id = "under"),
            feature(polygon(ring(-1.0, -1.0, 3.0, 3.0)), id = "over")
        )
        assertEquals("over", shapes.featureAt(LatLon(0.0, 0.0))?.id, "inside both")
        assertEquals("under", shapes.featureAt(LatLon(-1.5, -1.5))?.id, "inside only the one beneath")
    }

    @Test
    fun `a feature with no geometry is never nearest`() {
        val shapes = shapes(feature(null, id = "ghost"))
        assertNull(shapes.featureAt(LatLon(0.0, 0.0)))
        assertEquals("real", shapes(feature(null, "ghost"), feature(Geometry.Point(LatLon(50.0, 50.0)), "real")).featureAt(LatLon(0.0, 0.0))?.id)
    }

    @Test
    fun `nothing at all is nothing`() {
        assertNull(shapes().featureAt(LatLon(0.0, 0.0)))
    }

    @Test
    fun `a shape across the antimeridian is one shape`() {
        val across = shapes(feature(polygon(ring(179.0, -1.0, 181.0, 1.0).map { it.copy(lon = if (it.lon > 180) it.lon - 360 else it.lon) })))
        assertEquals(0.0, across.degreesTo(LatLon(0.0, 180.0)), "on the line itself")
        assertEquals(0.0, across.degreesTo(LatLon(0.0, 179.5)), "on the east side of it")
        assertEquals(0.0, across.degreesTo(LatLon(0.0, -179.5)), "on the west side")
        assertDegrees(1.0, across.degreesTo(LatLon(0.0, 178.0)))
        assertDegrees(1.0, across.degreesTo(LatLon(0.0, -178.0)))
        assertTrue(across.degreesTo(LatLon(0.0, 0.0))!! > 100.0, "the other side of the world is far")
    }

    @Test
    fun `a hole across the antimeridian is a hole`() {
        fun wrap(lon: Double) = if (lon > 180) lon - 360 else lon
        val shapes = shapes(
            feature(
                polygon(
                    ring(175.0, -5.0, 185.0, 5.0).map { it.copy(lon = wrap(it.lon)) },
                    ring(179.0, -1.0, 181.0, 1.0).map { it.copy(lon = wrap(it.lon)) }
                )
            )
        )
        assertEquals(0.0, shapes.degreesTo(LatLon(0.0, 177.0)))
        assertEquals(0.0, shapes.degreesTo(LatLon(0.0, -177.0)))
        assertDegrees(0.5, shapes.degreesTo(LatLon(0.0, 179.5)), "in the hole")
        assertDegrees(0.5, shapes.degreesTo(LatLon(0.0, -179.5)), "in the other half of the hole")
    }

    @Test
    fun `another copy of the world is the same place`() {
        val shapes = shapes(feature(box))
        val inside = LatLon(0.0, 1.0).let { listOf(it).toWorld() }
        assertEquals(0.0, shapes.nearest(inside[0] + 1.0, inside[1])?.distance)
        assertEquals(0.0, shapes.nearest(inside[0] - 1.0, inside[1])?.distance)
    }
}
