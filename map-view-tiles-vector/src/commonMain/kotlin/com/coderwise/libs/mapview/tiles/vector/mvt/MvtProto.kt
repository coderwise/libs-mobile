package com.coderwise.libs.mapview.tiles.vector.mvt

/**
 * A hand-rolled streaming reader for the Mapbox Vector Tile (MVT) protobuf envelope, version 2
 * (https://github.com/mapbox/vector-tile-spec/blob/master/2.1/vector_tile.proto).
 *
 * It walks the `.pbf` bytes once and builds the decoded [VectorTile] directly, instead of first
 * materialising a full kotlinx-serialization object graph (`MvtTile`/`MvtLayer`/`MvtFeature`/`MvtValue`
 * and their `List`s) and then mapping it. That intermediate graph — two passes' worth of `List`s,
 * boxed values, and per-feature geometry arrays, all thrown away once the [RenderTile] paths are
 * built — was the dominant decode-time garbage that stalled panning at high zoom (many dense tiles
 * stream in per second). Parsing straight into the flat output keeps the transient allocations to
 * each feature's command-stream buffer alone.
 *
 * Only the fields the renderer needs are read; everything else (feature ids, layer version, unknown
 * fields) is skipped by wire type. Truncated/garbage tails throw, which the caller already treats as
 * "drop this tile" via its surrounding `runCatching`.
 */
internal fun parseTile(
    bytes: ByteArray,
    keep: Set<String>?,
    attributeLayers: Set<String>?,
    classLayers: Map<String, String>? = null,
    nameLayers: Set<String>? = null,
): List<MvtLayer> {
    val reader = ProtoReader(bytes)
    val layers = ArrayList<MvtLayer>()
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        // Tile.layers = field 3, length-delimited message.
        if (tag.field == 3 && tag.wire == WIRE_LEN) {
            val end = reader.readBound()
            parseLayer(reader, end, keep, attributeLayers, classLayers, nameLayers)
                ?.let(layers::add)
            reader.pos = end
        } else {
            reader.skip(tag.wire)
        }
    }
    return layers
}

private fun parseLayer(
    reader: ProtoReader,
    end: Int,
    keep: Set<String>?,
    attributeLayers: Set<String>?,
    classLayers: Map<String, String>?,
    nameLayers: Set<String>?,
): MvtLayer? {
    var name = ""
    var extent = 4096
    val keys = ArrayList<String>()
    val values = ArrayList<Any?>()
    // Features may be encoded before the keys/values they reference, so record each feature's byte
    // span now and parse them in a second pass once keys/values for the layer are fully read.
    val featureStarts = IntBag(8)
    val featureEnds = IntBag(8)

    while (reader.pos < end) {
        val tag = reader.readTag()
        when {
            tag.field == 1 && tag.wire == WIRE_LEN -> name = reader.readString()      // name
            tag.field == 5 && tag.wire == WIRE_VARINT -> extent = reader.readVarintInt() // extent
            tag.field == 2 && tag.wire == WIRE_LEN -> {                                // features[]
                val fEnd = reader.readBound()
                featureStarts.add(reader.pos)
                featureEnds.add(fEnd)
                reader.pos = fEnd
            }
            tag.field == 3 && tag.wire == WIRE_LEN -> keys.add(reader.readString())    // keys[]
            tag.field == 4 && tag.wire == WIRE_LEN -> {                                // values[]
                val vEnd = reader.readBound()
                values.add(parseValue(reader, vEnd))
                reader.pos = vEnd
            }
            else -> reader.skip(tag.wire)                                              // version, unknown
        }
    }

    // A layer nobody draws costs nothing beyond the span-recording pass above: the features were
    // never parsed, only measured. The name arrives as field 1 and may follow the features in the
    // stream, which is why this cannot be decided any earlier.
    if (keep != null && name !in keep) return null

    val resolveAttrs = attributeLayers?.contains(name) == true
    // Located once for the layer: every feature's styling tag points at this same key index. Which
    // tag that is comes from the caller, because it isn't always `class` — boundaries are styled by
    // `admin_level`, and a country line and a parish line are the same class.
    val classKey = classLayers?.get(name)?.let { keys.indexOf(it) } ?: -1
    // The same trick for what a label says. A name is wanted for exactly the layers that carry
    // labels, and reading it out of a whole attribute map is the cost `featureClass` exists to
    // avoid -- so it gets a fast path of its own rather than forcing attribute resolution.
    val nameKey = if (nameLayers?.contains(name) == true) keys.indexOf(NAME_TAG) else -1
    val features = ArrayList<MvtFeature>(featureStarts.size)
    for (k in 0 until featureStarts.size) {
        reader.pos = featureStarts[k]
        features.add(
            parseFeature(reader, featureEnds[k], keys, values, resolveAttrs, classKey, nameKey)
        )
    }
    return MvtLayer(name, extent, features)
}

private fun parseFeature(
    reader: ProtoReader,
    end: Int,
    keys: List<String>,
    values: List<Any?>,
    resolveAttrs: Boolean,
    classKey: Int,
    nameKey: Int,
): MvtFeature {
    var type = GeometryType.UNKNOWN
    var tags: IntBag? = null
    var geometry: IntBag? = null

    while (reader.pos < end) {
        val tag = reader.readTag()
        when (tag.field) {
            2 -> if (resolveAttrs || classKey >= 0 || nameKey >= 0) {                    // tags (packed)
                val bag = tags ?: IntBag(8).also { tags = it }
                reader.readPackedOrSingle(bag, tag.wire)
            } else {
                reader.skip(tag.wire)
            }
            3 -> type = reader.readVarintInt().toGeometryType()                        // type
            4 -> {                                                                     // geometry (packed)
                val bag = geometry ?: IntBag(16).also { geometry = it }
                reader.readPackedOrSingle(bag, tag.wire)
            }
            else -> reader.skip(tag.wire)                                              // id, unknown
        }
    }

    val geom = geometry
    return MvtFeature(
        type = type,
        // Feed the command-stream buffer straight in (backing + length) — no exact-size copy.
        geometry = if (geom != null) decodeGeometry(type, geom.data, geom.size) else TileGeometry.EMPTY,
        attributes = if (resolveAttrs && tags != null) resolveAttributes(tags, keys, values) else emptyMap(),
        featureClass = if (classKey >= 0 && tags != null) classOf(tags, classKey, values) else null,
        name = if (nameKey >= 0 && tags != null) classOf(tags, nameKey, values) else null,
    )
}

/** Collapses an MVT `Value` message into its single populated field, matching the spec's oneof order. */
private fun parseValue(reader: ProtoReader, end: Int): Any? {
    var stringV: String? = null
    var floatV: Float? = null
    var doubleV: Double? = null
    var intV: Long? = null
    var uintV: Long? = null
    var sintV: Long? = null
    var boolV: Boolean? = null
    while (reader.pos < end) {
        val tag = reader.readTag()
        when (tag.field) {
            1 -> stringV = reader.readString()
            2 -> floatV = reader.readFloat()
            3 -> doubleV = reader.readDouble()
            4 -> intV = reader.readVarint()
            5 -> uintV = reader.readVarint()
            6 -> sintV = zigzagLong(reader.readVarint())
            7 -> boolV = reader.readVarint() != 0L
            else -> reader.skip(tag.wire)
        }
    }
    return stringV ?: boolV ?: doubleV ?: floatV ?: intV ?: uintV ?: sintV
}

/**
 * The value paired with [classKey] in a feature's packed `tags`, if it carries that key — a scan of
 * the tag pairs and one list lookup, with none of the allocation [resolveAttributes] does.
 */
private fun classOf(tags: IntBag, classKey: Int, values: List<Any?>): String? {
    var i = 0
    while (i + 1 < tags.size) {
        if (tags[i] == classKey) return tagValueOf(values.getOrNull(tags[i + 1]))
        i += 2
    }
    return null
}

/**
 * The styling tag's value as the string the style tables are keyed by. Not every such tag is a
 * string in the tile: `admin_level` is a number, and a source may encode the 6 in it as an int, a
 * uint or a double — all three have to find the same style entry, so "6.0" is not an answer.
 */
private fun tagValueOf(value: Any?): String? = when (value) {
    null -> null
    is String -> value
    is Number -> value.toDouble().let { d ->
        if (d.toLong().toDouble() == d) d.toLong().toString() else d.toString()
    }
    else -> value.toString()
}

/** Resolves a feature's packed `tags` (key/value index pairs) into a string-keyed attribute map. */
private fun resolveAttributes(tags: IntBag, keys: List<String>, values: List<Any?>): Map<String, Any?> {
    if (tags.size == 0) return emptyMap()
    val result = LinkedHashMap<String, Any?>(tags.size / 2)
    var i = 0
    while (i + 1 < tags.size) {
        val keyIndex = tags[i]
        val valueIndex = tags[i + 1]
        if (keyIndex in keys.indices && valueIndex in values.indices) {
            result[keys[keyIndex]] = values[valueIndex]
        }
        i += 2
    }
    return result
}

private fun Int.toGeometryType(): GeometryType = when (this) {
    1 -> GeometryType.POINT
    2 -> GeometryType.LINE
    3 -> GeometryType.POLYGON
    else -> GeometryType.UNKNOWN
}

/**
 * A cursor over a protobuf byte buffer. [pos] is public so callers can record/restore message spans
 * (needed for the layer's two-pass feature parse). Reads advance [pos]; bounds violations surface as
 * the array-index exceptions the caller already catches.
 */
internal class ProtoReader(private val buf: ByteArray) {
    var pos: Int = 0

    fun hasRemaining(): Boolean = pos < buf.size

    fun readTag(): Int = readVarintInt()

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = buf[pos++].toInt()
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    fun readVarintInt(): Int = readVarint().toInt()

    fun readLength(): Int = readVarintInt()

    /** Reads a length prefix and returns the absolute end position of the field it introduces. */
    fun readBound(): Int {
        val len = readLength()
        return pos + len
    }

    fun readString(): String {
        val len = readLength()
        val s = buf.decodeToString(pos, pos + len)
        pos += len
        return s
    }

    fun readFloat(): Float {
        val bits = (buf[pos].toInt() and 0xFF) or
            ((buf[pos + 1].toInt() and 0xFF) shl 8) or
            ((buf[pos + 2].toInt() and 0xFF) shl 16) or
            ((buf[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return Float.fromBits(bits)
    }

    fun readDouble(): Double {
        var bits = 0L
        for (k in 0 until 8) bits = bits or ((buf[pos + k].toLong() and 0xFF) shl (8 * k))
        pos += 8
        return Double.fromBits(bits)
    }

    /** Reads a repeated scalar field into [bag] — packed (one length-delimited run) or a single value. */
    fun readPackedOrSingle(bag: IntBag, wire: Int) {
        if (wire == WIRE_LEN) {
            val end = readBound()
            while (pos < end) bag.add(readVarint().toInt())
        } else {
            bag.add(readVarint().toInt())
        }
    }

    fun skip(wire: Int) {
        when (wire) {
            WIRE_VARINT -> readVarint()
            WIRE_64BIT -> pos += 8
            WIRE_LEN -> pos = readBound()
            WIRE_32BIT -> pos += 4
            else -> error("Unsupported wire type $wire")
        }
    }
}

private fun zigzagLong(value: Long): Long = (value ushr 1) xor -(value and 1)

/** A protobuf field tag splits into the field number and wire type: `(field shl 3) or wire`. */
private val Int.field: Int get() = this ushr 3
private val Int.wire: Int get() = this and 0x7

private const val WIRE_VARINT = 0
private const val WIRE_64BIT = 1
private const val WIRE_LEN = 2
private const val WIRE_32BIT = 5

/** The tag a label reads its text from. */
private const val NAME_TAG = "name"
