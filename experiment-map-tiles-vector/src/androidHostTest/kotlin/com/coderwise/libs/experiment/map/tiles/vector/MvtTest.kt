package com.coderwise.libs.experiment.map.tiles.vector

import com.coderwise.libs.experiment.map.tiles.vector.mvt.decodeMvt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Decodes a real OpenFreeMap tile: rural England, z14, roughly 52.5N 1.5W. */
class MvtTest {

    private val layers by lazy {
        val bytes = javaClass.getResourceAsStream("/rural-14-8123-5390.pbf")!!.readBytes()
        decodeMvt(bytes, setOf("landuse", "landcover", "transportation"))
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
        assertTrue(roads.all { it.type == 2 || it.type == 3 }, "roads should be lines or areas")
        assertTrue(roads.any { it.kind == "minor" }, roads.map { it.kind }.distinct().toString())
        val coordinates = roads.flatMap { it.rings }.flatMap { it.asList() }
        assertTrue(coordinates.isNotEmpty())
        assertTrue(coordinates.all { it > -1024f && it < 5120f },
            "coordinates should sit in or just outside the 0..4096 tile")
    }

    @Test
    fun `land use polygons are closed and classified`() {
        val land = layers.filter { it.name != "transportation" }.flatMap { it.features }
        assertTrue(land.size > 10, "only ${land.size} land features")
        val polygons = land.filter { it.type == 3 }
        assertTrue(polygons.isNotEmpty())
        assertTrue(
            polygons.all { feature ->
                feature.rings.all { it[0] == it[it.size - 2] && it[1] == it[it.size - 1] }
            },
            "every polygon ring should close"
        )
        assertTrue(land.any { it.kind.isNotEmpty() }, "nothing was classified")
    }
}
