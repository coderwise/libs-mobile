package com.coderwise.libs.mapview.tiles.vector

import com.coderwise.libs.mapview.tiles.vector.mvt.GeometryType
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtFeature
import com.coderwise.libs.mapview.tiles.vector.mvt.decodeMvt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Decodes a real OpenFreeMap tile: rural England, z14, roughly 52.5N 1.5W. */
class MvtTest {

    private val bytes by lazy {
        javaClass.getResourceAsStream("/rural-14-8123-5390.pbf")!!.readBytes()
    }

    private val layers by lazy {
        decodeMvt(
            bytes,
            keep = setOf("landuse", "landcover", "transportation"),
            classLayers = mapOf(
                "landuse" to "class",
                "landcover" to "class",
                "transportation" to "class"
            )
        )
    }

    @Test
    fun `only the asked-for layers come back`() {
        assertEquals(setOf("landcover", "landuse", "transportation"), layers.map { it.name }.toSet())
    }

    @Test
    fun `tiles use the usual extent`() {
        assertTrue(layers.all { it.extent == 4096 }, layers.map { "${it.name}=${it.extent}" }.toString())
    }

    @Test
    fun `roads are lines with a class and real coordinates`() {
        val roads = layers.single { it.name == "transportation" }.features
        assertTrue(roads.size > 20, "only ${roads.size} roads")
        assertTrue(
            roads.all { it.type == GeometryType.LINE || it.type == GeometryType.POLYGON },
            "roads should be lines or areas"
        )
        assertTrue(
            roads.any { it.featureClass == "minor" },
            roads.map { it.featureClass }.distinct().toString()
        )
        val coordinates = roads.flatMap { it.geometry.coords.asList() }
        assertTrue(coordinates.isNotEmpty())
        assertTrue(
            coordinates.all { it > -1024 && it < 5120 },
            "coordinates should sit in or just outside the 0..4096 tile"
        )
    }

    @Test
    fun `land use polygons are closed and classified`() {
        val land = layers.filter { it.name != "transportation" }.flatMap { it.features }
        assertTrue(land.size > 10, "only ${land.size} land features")
        val polygons = land.filter { it.type == GeometryType.POLYGON }
        assertTrue(polygons.isNotEmpty())
        assertTrue(polygons.all { it.ringsClose() }, "every polygon ring should close")
        assertTrue(land.any { it.featureClass != null }, "nothing was classified")
    }

    @Test
    fun `a layer left out of keep is not decoded at all`() {
        // The saving this exists for: a layer nobody draws is measured and skipped, not parsed.
        val one = decodeMvt(bytes, keep = setOf("water"))
        assertEquals(listOf("water"), one.map { it.name })

        // And null means everything, which is what a caller gets for not saying.
        assertTrue(decodeMvt(bytes).size > one.size)
    }

    @Test
    fun `a name is resolved only for the layers that asked for one`() {
        // The point of the fast path: a label's text without a whole attribute map per feature.
        val named = decodeMvt(bytes, keep = setOf("transportation_name"), nameLayers = setOf("transportation_name"))
            .single().features
        assertTrue(named.any { it.name != null }, "no road name came back")
        assertTrue(named.all { it.attributes.isEmpty() }, "names should not drag attributes along")

        val unnamed = decodeMvt(bytes, keep = setOf("transportation_name")).single().features
        assertTrue(unnamed.all { it.name == null }, "a layer that did not ask should pay nothing")
    }

    @Test
    fun `a class is read from the tag its layer is keyed on`() {
        // Not always `class`: a boundary is told apart by admin_level, and asking for the wrong tag
        // has to come back empty rather than guess.
        val wrongTag = decodeMvt(
            bytes,
            keep = setOf("transportation"),
            classLayers = mapOf("transportation" to "admin_level")
        ).single().features
        assertTrue(wrongTag.all { it.featureClass == null })
        assertNull(wrongTag.firstOrNull { it.featureClass != null })
    }

    /** Whether every ring of this feature ends where it began. */
    private fun MvtFeature.ringsClose(): Boolean = (0 until geometry.partCount).all { part ->
        val start = geometry.partStarts[part]
        val end = geometry.partEnd(part)
        end - start >= 3 &&
            geometry.coords[2 * start] == geometry.coords[2 * (end - 1)] &&
            geometry.coords[2 * start + 1] == geometry.coords[2 * (end - 1) + 1]
    }
}
