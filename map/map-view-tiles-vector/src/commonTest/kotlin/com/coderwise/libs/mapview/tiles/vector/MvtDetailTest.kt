package com.coderwise.libs.mapview.tiles.vector

import com.coderwise.libs.mapview.tiles.vector.mvt.decodeMvt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The further tags a style can ask for. A layer pays for them only when asked, and a feature that
 * lacks one answers null rather than shifting the others along.
 */
class MvtDetailTest {

    private val wanted = mapOf(LAYER to listOf("subclass", "brunnel"))

    @Test
    fun `the tags asked for come back in the order asked`() {
        val feature = decoded(tile("class" to "primary", "subclass" to "link", "brunnel" to "tunnel"))
        assertEquals(listOf("link", "tunnel"), feature.details)
    }

    @Test
    fun `a tag the feature does not carry is null in its place`() {
        val feature = decoded(tile("class" to "primary", "brunnel" to "bridge"))
        assertEquals(listOf(null, "bridge"), feature.details)
    }

    @Test
    fun `a tag the tile has no key for at all is null`() {
        assertEquals(listOf(null, null), decoded(tile("class" to "primary")).details)
    }

    @Test
    fun `a layer says which tags its details are and hands them out by name`() {
        val layer = decode(tile("class" to "primary", "subclass" to "link", "brunnel" to "tunnel"))
        assertEquals(listOf("subclass", "brunnel"), layer.detailTags)
        val feature = layer.features.single()
        assertEquals("tunnel", layer.detail(feature, "brunnel"))
        assertEquals("link", layer.detail(feature, "subclass"))
        // A tag that was not asked for is not there, rather than being some other tag.
        assertNull(layer.detail(feature, "class"))
    }

    @Test
    fun `the order tags were asked for in is the order of details`() {
        val layer = decodeMvt(
            tile("class" to "primary", "subclass" to "link", "brunnel" to "tunnel"),
            detailLayers = mapOf(LAYER to listOf("brunnel", "subclass"))
        ).single()
        assertEquals(listOf("brunnel", "subclass"), layer.detailTags)
        assertEquals(listOf("tunnel", "link"), layer.features.single().details)
        assertEquals("link", layer.detail(layer.features.single(), "subclass"))
    }

    @Test
    fun `a layer that was not asked has no detail tags`() {
        val layer = decodeMvt(tile("class" to "primary")).single()
        assertTrue(layer.detailTags.isEmpty())
        assertNull(layer.detail(layer.features.single(), "subclass"))
    }

    @Test
    fun `the class is still resolved alongside`() {
        val feature = decoded(tile("class" to "primary", "brunnel" to "bridge"))
        assertEquals("primary", feature.featureClass)
    }

    @Test
    fun `a layer that was not asked has no details`() {
        val layers = decodeMvt(
            tile("class" to "primary", "brunnel" to "bridge"),
            classLayers = mapOf(LAYER to "class"),
            detailLayers = mapOf("somewhere_else" to listOf("brunnel"))
        )
        assertTrue(layers.single().features.single().details.isEmpty())
    }

    @Test
    fun `without detailLayers nothing is resolved`() {
        val layers = decodeMvt(tile("class" to "primary", "brunnel" to "bridge"))
        assertTrue(layers.single().features.single().details.isEmpty())
        assertNull(layers.single().features.single().featureClass)
    }

    private fun decode(bytes: ByteArray) = decodeMvt(
        bytes,
        classLayers = mapOf(LAYER to "class"),
        detailLayers = wanted
    ).single()

    private fun decoded(bytes: ByteArray) = decode(bytes).features.single()

    /** One layer, one point feature tagged with [tags]. */
    private fun tile(vararg tags: Pair<String, String>): ByteArray {
        val feature = buildList {
            addVarint(1, 1)                                           // id
            addBytes(2, tags.indices.flatMap { (varint(it) + varint(it)).toList() }.toByteArray())
            addVarint(3, 1)                                           // type: POINT
            addBytes(4, varint(9) + varint(10) + varint(10))          // MoveTo(1), one (x, y)
        }
        val layer = buildList {
            addVarint(15, 2)                                          // version
            addBytes(1, LAYER.encodeToByteArray())                    // name
            addBytes(2, feature.toByteArray())                        // features[]
            tags.forEach { addBytes(3, it.first.encodeToByteArray()) }          // keys[]
            tags.forEach { addBytes(4, bytes(1, it.second.encodeToByteArray())) } // values[]
            addVarint(5, EXTENT)                                      // extent
        }
        return bytes(3, layer.toByteArray())                          // Tile.layers[]
    }

    private fun MutableList<Byte>.addBytes(number: Int, payload: ByteArray) =
        addAll(bytes(number, payload).toList())

    private fun MutableList<Byte>.addVarint(number: Int, value: Int) =
        addAll((varint((number shl 3) or WIRE_VARINT) + varint(value)).toList())

    private fun bytes(number: Int, payload: ByteArray): ByteArray =
        varint((number shl 3) or WIRE_LEN) + varint(payload.size) + payload

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

    private companion object {
        const val LAYER = "transportation"
        const val EXTENT = 4096
        const val WIRE_VARINT = 0
        const val WIRE_LEN = 2
    }
}
