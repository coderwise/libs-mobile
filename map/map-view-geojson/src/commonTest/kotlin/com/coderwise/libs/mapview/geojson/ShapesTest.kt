package com.coderwise.libs.mapview.geojson

import com.coderwise.libs.mapview.LatLon
import com.coderwise.libs.mapview.toWorld
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Flattening geometries into the shapes that are drawn and touched. */
class ShapesTest {

    @Test
    fun `a point is a pair of world coordinates`() {
        val at = LatLon(51.5, -0.12)
        val shaped = collection(feature(Geometry.Point(at))).toShapes().features.single()
        assertContentEquals(listOf(at).toWorld(), shaped.points)
    }

    @Test
    fun `a multi-geometry is all its parts`() {
        val shaped = collection(
            feature(Geometry.MultiPoint(listOf(LatLon(1.0, 1.0), LatLon(2.0, 2.0), LatLon(3.0, 3.0))))
        ).toShapes().features.single()
        assertEquals(6, shaped.points.size)

        val lines = collection(
            feature(Geometry.MultiLineString(listOf(listOf(LatLon(0.0, 0.0), LatLon(1.0, 1.0)), listOf(LatLon(2.0, 2.0), LatLon(3.0, 3.0)))))
        ).toShapes().features.single()
        assertEquals(2, lines.lines.size)

        val polygons = collection(
            feature(Geometry.MultiPolygon(listOf(listOf(square(0.0, 0.0, 1.0), square(0.0, 0.0, 0.5)), listOf(square(5.0, 5.0, 1.0)))))
        ).toShapes().features.single()
        assertEquals(listOf(2, 1), polygons.polygons.map { it.size }, "rings of each polygon")
    }

    @Test
    fun `a collection is flattened`() {
        val shaped = collection(
            feature(
                Geometry.GeometryCollection(
                    listOf(
                        Geometry.Point(LatLon(1.0, 1.0)),
                        Geometry.GeometryCollection(listOf(Geometry.LineString(listOf(LatLon(0.0, 0.0), LatLon(1.0, 1.0))))),
                        polygon(square(0.0, 0.0, 1.0))
                    )
                )
            )
        ).toShapes().features.single()
        assertEquals(2, shaped.points.size)
        assertEquals(1, shaped.lines.size)
        assertEquals(1, shaped.polygons.size)
    }

    @Test
    fun `a feature without a geometry has no shapes but is kept`() {
        val shapes = collection(feature(null, id = "ghost")).toShapes()
        val shaped = shapes.features.single()
        assertEquals("ghost", shaped.feature.id)
        assertEquals(0, shaped.points.size + shaped.lines.size + shaped.polygons.size)
    }

    @Test
    fun `a line of one point and a ring of none are not shapes`() {
        val shaped = collection(
            feature(Geometry.LineString(listOf(LatLon(1.0, 1.0)))),
        ).toShapes().features.single()
        assertEquals(0, shaped.lines.size)
        val empty = collection(feature(polygon(emptyList()))).toShapes().features.single()
        assertEquals(listOf(0), empty.polygons.map { it.size })
    }

    @Test
    fun `a layer is shown from its minimum zoom up`() {
        assertEquals(false, shownAt(11.99f, 12f))
        assertEquals(true, shownAt(12f, 12f))
        assertEquals(true, shownAt(15f, 12f))
        assertEquals(true, shownAt(1f, 0f))
    }
}
