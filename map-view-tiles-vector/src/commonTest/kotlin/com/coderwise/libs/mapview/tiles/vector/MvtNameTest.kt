package com.coderwise.libs.mapview.tiles.vector

import com.coderwise.libs.mapview.tiles.vector.mvt.decodeMvt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which key a name is read from. Sources disagree: most carry a plain `name`, some ship only the
 * latinised `name:latin`, so the decoder tries them in order and a leaner source still labels.
 */
class MvtNameTest {

    @Test
    fun `a plain name is preferred over the latinised one`() {
        val bytes = tile("name" to "Reykjavík", "name:latin" to "Reykjavik")
        assertEquals("Reykjavík", decoded(bytes))
    }

    @Test
    fun `the latinised name answers when the source ships no plain one`() {
        assertEquals("Eyjafjallajokull", decoded(tile("name:latin" to "Eyjafjallajokull")))
    }

    @Test
    fun `the english name answers when neither of the others is there`() {
        assertEquals("Iceland", decoded(tile("name:en" to "Iceland")))
    }

    @Test
    fun `a name key the decoder does not ask for is not a name`() {
        assertNull(decoded(tile("name:de" to "Island")))
    }

    @Test
    fun `a layer outside nameLayers carries no name at all`() {
        val layers = decodeMvt(tile("name" to "Reykjavík"), nameLayers = setOf("somewhere_else"))
        assertNull(layers.single().features.single().name)
    }

    private fun decoded(bytes: ByteArray): String? =
        decodeMvt(bytes, nameLayers = setOf(LAYER)).single().features.single().name

    /** One layer, one point feature, tagged with [tags] — the smallest tile that can carry a name. */
    private fun tile(vararg tags: Pair<String, String>): ByteArray {
        val keys = tags.map { it.first }
        val values = tags.map { it.second }

        val feature = buildList {
            addVarint(1, 1)                                           // id
            // tags: key index, value index, alternating.
            addBytes(2, tags.indices.flatMap { (varint(it) + varint(it)).toList() }.toByteArray())
            addVarint(3, 1)                                           // type: POINT
            // geometry: MoveTo(1), then one zigzagged (x, y).
            addBytes(4, varint(9) + varint(10) + varint(10))
        }

        val layer = buildList {
            addVarint(15, 2)                                          // version
            addBytes(1, LAYER.encodeToByteArray())                    // name
            addBytes(2, feature.toByteArray())                        // features[]
            keys.forEach { addBytes(3, it.encodeToByteArray()) }      // keys[]
            // values[]: each a Value message whose field 1 is the string.
            values.forEach { addBytes(4, bytes(1, it.encodeToByteArray())) }
            addVarint(5, EXTENT)                                      // extent
        }

        return bytes(3, layer.toByteArray())                          // Tile.layers[]
    }

    private fun MutableList<Byte>.addBytes(number: Int, payload: ByteArray) =
        addAll(bytes(number, payload).toList())

    private fun MutableList<Byte>.addVarint(number: Int, value: Int) =
        addAll((varint((number shl 3) or WIRE_VARINT) + varint(value)).toList())

    /** One length-delimited protobuf field: its tag, its length, then its payload. */
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
        const val LAYER = "place"
        const val EXTENT = 4096
        const val WIRE_VARINT = 0
        const val WIRE_LEN = 2
    }
}
