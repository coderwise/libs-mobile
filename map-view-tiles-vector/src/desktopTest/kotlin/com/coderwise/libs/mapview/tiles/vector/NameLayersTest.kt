package com.coderwise.libs.mapview.tiles.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The names a tile offers beyond places and roads: points of interest, peaks, lakes, rivers. Built
 * from a hand-made tile, so what comes out is exactly what went in, in the order the style asks
 * for — which is the order a collision is settled in.
 */
class NameLayersTest {

    private val everything = tile(
        "place" to listOf(point(2048, 2048, "name" to "Town", "class" to "city")),
        "water_name" to listOf(
            point(1000, 1000, "name" to "Lake Tahoe", "class" to "lake"),
            line(500, 3000, 3500, 3000, "name" to "River Thames", "class" to "river")
        ),
        "waterway" to listOf(line(500, 3500, 3500, 3500, "name" to "Brook", "class" to "stream")),
        "poi" to listOf(
            point(3000, 3000, "name" to "Museum", "rank" to "5", "class" to "museum"),
            point(3200, 3200, "name" to "Park", "rank" to "2", "class" to "park")
        ),
        "mountain_peak" to listOf(point(300, 300, "name" to "Mont Blanc", "rank" to "1")),
        "transportation_name" to listOf(line(500, 500, 3500, 500, "name" to "High Street"))
    )

    private fun labels(style: VectorStyle = VectorStyle(), bytes: ByteArray = everything): List<Label> =
        runBlocking { assertNotNull(decodeVectorTile(14, bytes, style)).labels }

    private fun names(style: VectorStyle = VectorStyle()) = labels(style).map { it.text }

    @Test
    fun `every kind is named by default`() {
        assertEquals(
            setOf("Town", "Lake Tahoe", "Mont Blanc", "Park", "Museum", "High Street", "River Thames", "Brook"),
            names().toSet()
        )
    }

    @Test
    fun `names are in the order a collision is settled in`() {
        // Places, water bodies, peaks and points of interest by rank, roads, then rivers.
        assertEquals(
            listOf("Town", "Lake Tahoe", "Mont Blanc", "Park", "Museum", "High Street", "River Thames", "Brook"),
            names()
        )
    }

    @Test
    fun `only water is marked as water`() {
        val water = labels().filter { it.water }.map { it.text }
        assertEquals(setOf("Lake Tahoe", "River Thames", "Brook"), water.toSet())
    }

    @Test
    fun `lakes are level and rivers follow their line`() {
        val byName = labels().associateBy { it.text }
        assertTrue(byName.getValue("Lake Tahoe").upright)
        assertFalse(byName.getValue("River Thames").upright)
        assertFalse(byName.getValue("Brook").upright)
    }

    @Test
    fun `a point of interest is named where its point is`() {
        val park = labels().single { it.text == "Park" }
        assertEquals(3200f / 4096f, park.x, absoluteTolerance = 1e-4f)
        assertEquals(3200f / 4096f, park.y, absoluteTolerance = 1e-4f)
    }

    @Test
    fun `each kind can be switched off alone`() {
        val all = names().toSet()
        fun without(style: VectorStyle, vararg gone: String) =
            assertEquals(all - gone.toSet(), names(style).toSet())

        without(VectorStyle(poiName = NameStyle(9f, enabled = false)), "Park", "Museum")
        without(VectorStyle(peakName = NameStyle(9f, enabled = false)), "Mont Blanc")
        without(VectorStyle(waterName = NameStyle(9f, enabled = false)), "Lake Tahoe")
        without(VectorStyle(riverName = NameStyle(9f, enabled = false)), "River Thames", "Brook")
        without(VectorStyle(roadName = null), "High Street")
    }

    @Test
    fun `a kind's size is the one in its style`() {
        val style = VectorStyle(poiName = NameStyle(size = 17f), riverName = NameStyle(size = 13f))
        val byName = labels(style).associateBy { it.text }
        assertEquals(17f, byName.getValue("Park").size)
        assertEquals(13f, byName.getValue("Brook").size)
    }

    @Test
    fun `a feature with no rank goes after the ranked ones`() {
        val bytes = tile(
            "poi" to listOf(
                point(1000, 1000, "name" to "Unranked"),
                point(2000, 2000, "name" to "Ranked", "rank" to "9")
            )
        )
        assertEquals(listOf("Ranked", "Unranked"), labels(bytes = bytes).map { it.text })
    }

    @Test
    fun `a tile cannot fill its budget with points of interest`() {
        val many = (0 until 200).map { point(10 + it, 10 + it, "name" to "Poi $it", "rank" to "$it") }
        val bytes = tile(
            "poi" to many,
            "transportation_name" to listOf(line(500, 500, 3500, 500, "name" to "High Street"))
        )
        val names = labels(bytes = bytes).map { it.text }
        assertTrue("High Street" in names, "the road lost its place to points of interest")
        assertTrue(names.count { it.startsWith("Poi") } <= 32, "${names.size} names")
        assertEquals("Poi 0", names.first(), "the most important point of interest comes first")
    }

    @Test
    fun `layers that only carry names draw nothing`() {
        // `water_name` holds river lines that waterway already draws; left to the geometry
        // fallback they would be drawn again, in grey.
        val bytes = tile(
            "water_name" to listOf(line(500, 3000, 3500, 3000, "name" to "River Thames")),
            "poi" to listOf(point(3000, 3000, "name" to "Park", "rank" to "1")),
            "mountain_peak" to listOf(point(300, 300, "name" to "Mont Blanc", "rank" to "1"))
        )
        val decoded = runBlocking { assertNotNull(decodeVectorTile(14, bytes)) }
        assertTrue(decoded.render.ops.isEmpty(), "name-only layers were drawn")
        assertEquals(3, decoded.labels.size)
    }

    @Test
    fun `a style compares equal when built the same way`() {
        assertEquals(VectorStyle(), VectorStyle())
        assertEquals(NameStyle(9f), NameStyle(9f, enabled = true))
    }

    // --- a tile, by hand -----------------------------------------------------------------------

    private class Feature(val type: Int, val geometry: List<Int>, val tags: List<Pair<String, String>>)

    private fun point(x: Int, y: Int, vararg tags: Pair<String, String>) =
        Feature(1, listOf(9, zigzag(x), zigzag(y)), tags.toList())

    private fun line(x1: Int, y1: Int, x2: Int, y2: Int, vararg tags: Pair<String, String>) =
        Feature(2, listOf(9, zigzag(x1), zigzag(y1), (1 shl 3) or 2, zigzag(x2 - x1), zigzag(y2 - y1)), tags.toList())

    private fun zigzag(n: Int) = (n shl 1) xor (n shr 31)

    private fun tile(vararg layers: Pair<String, List<Feature>>): ByteArray =
        layers.fold(ByteArray(0)) { acc, (name, features) -> acc + field(3, layer(name, features)) }

    private fun layer(name: String, features: List<Feature>): ByteArray {
        val keys = features.flatMap { f -> f.tags.map { it.first } }.distinct()
        val values = features.flatMap { f -> f.tags.map { it.second } }.distinct()
        var out = varintField(15, 2) + field(1, name.encodeToByteArray())
        features.forEach { f ->
            val tags = f.tags.flatMap { listOf(keys.indexOf(it.first), values.indexOf(it.second)) }
            out += field(2, varintField(3, f.type) + packed(2, tags) + packed(4, f.geometry))
        }
        keys.forEach { out += field(3, it.encodeToByteArray()) }
        values.forEach { out += field(4, field(1, it.encodeToByteArray())) }
        return out + varintField(5, 4096)
    }

    private fun packed(number: Int, values: List<Int>) =
        field(number, values.fold(ByteArray(0)) { acc, v -> acc + varint(v) })

    private fun varintField(number: Int, value: Int) = varint(number shl 3) + varint(value)

    private fun field(number: Int, payload: ByteArray) =
        varint((number shl 3) or 2) + varint(payload.size) + payload

    private fun varint(value: Int): ByteArray {
        var v = value
        val out = ArrayList<Byte>()
        while (true) {
            val b = v and 0x7F
            v = v ushr 7
            if (v == 0) { out.add(b.toByte()); return out.toByteArray() }
            out.add((b or 0x80).toByte())
        }
    }
}
