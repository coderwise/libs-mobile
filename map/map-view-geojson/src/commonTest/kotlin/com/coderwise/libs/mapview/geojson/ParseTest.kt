package com.coderwise.libs.mapview.geojson

import com.coderwise.libs.mapview.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParseTest {

    private fun geometry(json: String) = parseGeoJson(json).features.single().geometry

    @Test
    fun `a position is longitude then latitude and comes out latitude first`() {
        assertEquals(
            Geometry.Point(LatLon(51.5, -0.12)),
            geometry("""{"type":"Point","coordinates":[-0.12,51.5]}""")
        )
    }

    @Test
    fun `an altitude is ignored`() {
        assertEquals(
            Geometry.Point(LatLon(51.5, -0.12)),
            geometry("""{"type":"Point","coordinates":[-0.12,51.5,35.2]}""")
        )
    }

    @Test
    fun `every kind of geometry is read`() {
        val collection = parseGeoJson(
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"MultiPoint","coordinates":[[1,2],[3,4]]}},
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[1,2],[3,4],[5,6]]}},
              {"type":"Feature","geometry":{"type":"MultiLineString","coordinates":[[[1,2],[3,4]],[[5,6],[7,8]]]}},
              {"type":"Feature","geometry":{"type":"Polygon","coordinates":[[[0,0],[4,0],[4,4],[0,0]],[[1,1],[2,1],[2,2],[1,1]]]}},
              {"type":"Feature","geometry":{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]],[[[5,5],[6,5],[6,6],[5,5]]]]}},
              {"type":"Feature","geometry":{"type":"GeometryCollection","geometries":[
                {"type":"Point","coordinates":[9,8]},{"type":"LineString","coordinates":[[1,2],[3,4]]}]}}
            ]}
            """.trimIndent()
        ).features.map { it.geometry }

        assertEquals(Geometry.MultiPoint(listOf(LatLon(2.0, 1.0), LatLon(4.0, 3.0))), collection[0])
        assertEquals(Geometry.LineString(listOf(LatLon(2.0, 1.0), LatLon(4.0, 3.0), LatLon(6.0, 5.0))), collection[1])
        assertEquals(
            Geometry.MultiLineString(
                listOf(listOf(LatLon(2.0, 1.0), LatLon(4.0, 3.0)), listOf(LatLon(6.0, 5.0), LatLon(8.0, 7.0)))
            ),
            collection[2]
        )
        val polygon = collection[3] as Geometry.Polygon
        assertEquals(2, polygon.rings.size, "outer ring and one hole")
        assertEquals(LatLon(4.0, 4.0), polygon.rings[0][2])
        val multi = collection[4] as Geometry.MultiPolygon
        assertEquals(2, multi.polygons.size)
        assertEquals(LatLon(5.0, 5.0), multi.polygons[1][0][0])
        assertEquals(
            Geometry.GeometryCollection(
                listOf(Geometry.Point(LatLon(8.0, 9.0)), Geometry.LineString(listOf(LatLon(2.0, 1.0), LatLon(4.0, 3.0))))
            ),
            collection[5]
        )
    }

    @Test
    fun `a feature keeps its properties and its id`() {
        val feature = parseGeoJson(
            """
            {"type":"Feature","id":"a-1","geometry":null,
             "properties":{"name":"Trail","lanes":2,"width":1.5,"open":true,"note":null,
                           "tags":["x",1],"nested":{"k":"v"}}}
            """.trimIndent()
        ).features.single()

        assertEquals("a-1", feature.id)
        assertNull(feature.geometry)
        assertEquals(
            mapOf(
                "name" to "Trail", "lanes" to 2.0, "width" to 1.5, "open" to true, "note" to null,
                "tags" to listOf("x", 1.0), "nested" to mapOf("k" to "v")
            ),
            feature.properties
        )
    }

    @Test
    fun `a numeric id is written without a fraction when it has none`() {
        fun id(json: String) = parseGeoJson("""{"type":"Feature","geometry":null,"id":$json}""").features.single().id
        assertEquals("7", id("7"))
        assertEquals("7.5", id("7.5"))
        assertEquals("-3", id("-3"))
        assertNull(id("true"))
    }

    @Test
    fun `a feature with no properties has none`() {
        val feature = parseGeoJson("""{"type":"Feature","geometry":null,"properties":null}""").features.single()
        assertEquals(emptyMap(), feature.properties)
        assertNull(feature.id)
    }

    @Test
    fun `a single feature is a collection of one`() {
        val collection = parseGeoJson("""{"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]}}""")
        assertEquals(1, collection.features.size)
    }

    @Test
    fun `a bare geometry is a collection of one feature without properties`() {
        val feature = parseGeoJson("""{"type":"Point","coordinates":[1,2]}""").features.single()
        assertEquals(Geometry.Point(LatLon(2.0, 1.0)), feature.geometry)
        assertEquals(emptyMap(), feature.properties)
    }

    @Test
    fun `an empty collection is empty`() {
        assertEquals(emptyList(), parseGeoJson("""{"type":"FeatureCollection","features":[]}""").features)
    }

    @Test
    fun `members GeoJSON allows and the model leaves out are skipped`() {
        val collection = parseGeoJson(
            """
            {"type":"FeatureCollection","bbox":[0,0,1,1],"crs":{"type":"name"},"extra":[{"deep":[1,[2]]}],
             "features":[{"type":"Feature","bbox":[0,0,1,1],"geometry":{"type":"Point","coordinates":[1,2]}}]}
            """.trimIndent()
        )
        assertEquals(1, collection.features.size)
    }

    @Test
    fun `strings are unescaped`() {
        val properties = parseGeoJson(
            """{"type":"Feature","geometry":null,"properties":{"s":"a\"b\\c\/d\n\té😀"}}"""
        ).features.single().properties
        assertEquals("a\"b\\c/d\n\té😀", properties["s"])
    }

    @Test
    fun `numbers are read in every form JSON has`() {
        val coordinates = (geometry("""{"type":"LineString","coordinates":[[-1.5e1,2E-1],[0,0]]}""") as Geometry.LineString)
        assertEquals(LatLon(0.2, -15.0), coordinates.positions[0])
        assertEquals(LatLon(0.0, 0.0), coordinates.positions[1])
    }

    @Test
    fun `whitespace and a byte order mark are allowed around the document`() {
        val json = "﻿ \n\t{ \"type\" : \"Point\" , \"coordinates\" : [ 1 , 2 ] }\r\n "
        assertEquals(Geometry.Point(LatLon(2.0, 1.0)), geometry(json))
    }

    @Test
    fun `text that is not JSON is refused`() {
        for (json in listOf("", "{", """{"type":"Point",""", "nope", """{"type":"Point"} x""", "[1,2", """{"a" 1}""", "\"abc")) {
            assertFailsWith<IllegalArgumentException>("accepted <$json>") { parseGeoJson(json) }
        }
    }

    @Test
    fun `JSON that is not GeoJSON is refused`() {
        val refused = listOf(
            "[1,2]",                                                     // not an object
            """{"coordinates":[1,2]}""",                                 // no type
            """{"type":"Circle","coordinates":[1,2]}""",                 // unknown type
            """{"type":"Point","coordinates":[1]}""",                    // no latitude
            """{"type":"Point","coordinates":["a","b"]}""",              // not numbers
            """{"type":"Point","coordinates":[1e999,2]}""",              // not finite
            """{"type":"LineString","coordinates":[1,2]}""",             // positions, not a position
            """{"type":"FeatureCollection"}""",                          // no features
            """{"type":"FeatureCollection","features":[{"type":"Point","coordinates":[1,2]}]}""", // a geometry
            """{"type":"Feature","geometry":3}""",                       // not a geometry
        )
        for (json in refused) assertFailsWith<IllegalArgumentException>("accepted <$json>") { parseGeoJson(json) }
    }

    @Test
    fun `the refusal says where the text went wrong`() {
        val failure = assertFailsWith<IllegalArgumentException> { parseGeoJson("""{"type":"Point","coordinates":[1,?]}""") }
        assertTrue("at 33" in failure.message!!, failure.message)
    }

    @Test
    fun `nesting that would overflow the stack is refused`() {
        val json = "[".repeat(10_000) + "]".repeat(10_000)
        assertFailsWith<IllegalArgumentException> { parseGeoJson(json) }
    }
}
