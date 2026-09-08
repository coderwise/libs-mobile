package com.coderwise.libs.experiment.map.tiles.vector.mvt

/** A layer of a Mapbox Vector Tile, pared down to what drawing one needs. */
class MvtLayer(val name: String, val extent: Int, val features: List<MvtFeature>)

/**
 * [type] is 1 point, 2 line, 3 polygon. [kind] is the feature's `class` tag — "motorway",
 * "residential", "city" — which is what the styling looks at; [name] is what a label says, and is
 * empty for everything that is not one.
 *
 * A ring is its points flat: `x0, y0, x1, y1, …`, tile-local in `0..extent`. Flat because a tile
 * holds tens of thousands of points and a list of them is a boxed object each, which is enough
 * allocation to keep the collector running through every pan.
 */
class MvtFeature(val type: Int, val kind: String, val name: String, val rings: List<FloatArray>)

/**
 * Decodes the [keep] layers of a vector tile. Hand-rolled protobuf: MVT uses a handful of fields,
 * so a reader for varints and length-delimited chunks is enough, and skips everything else.
 * https://github.com/mapbox/vector-tile-spec
 */
fun decodeMvt(data: ByteArray, keep: Set<String>): List<MvtLayer> {
    val layers = mutableListOf<MvtLayer>()
    val reader = Reader(data, 0, data.size)
    while (reader.pos < reader.end) {
        val tag = reader.int()
        if (tag ushr 3 == 3 && tag and 7 == 2) {
            reader.chunk { start, end -> layer(data, start, end, keep)?.let(layers::add) }
        } else {
            reader.skip(tag and 7)
        }
    }
    return layers
}

private fun layer(data: ByteArray, start: Int, end: Int, keep: Set<String>): MvtLayer? {
    var name = ""
    var extent = 4096
    val keys = mutableListOf<String>()
    val values = mutableListOf<String>()
    val featureRanges = mutableListOf<Int>() // start, end pairs
    val reader = Reader(data, start, end)

    while (reader.pos < end) {
        val tag = reader.int()
        when (tag ushr 3) {
            1 -> name = reader.string()
            5 -> extent = reader.int()
            3 -> keys += reader.string()
            4 -> reader.chunk { s, e -> values += value(data, s, e) }
            2 -> reader.chunk { s, e -> featureRanges += s; featureRanges += e }
            else -> reader.skip(tag and 7)
        }
    }
    if (name !in keep) return null

    val features = ArrayList<MvtFeature>(featureRanges.size / 2)
    for (i in featureRanges.indices step 2) {
        features += feature(data, featureRanges[i], featureRanges[i + 1], keys, values)
    }
    return MvtLayer(name, extent, features)
}

private fun feature(
    data: ByteArray,
    start: Int,
    end: Int,
    keys: List<String>,
    values: List<String>
): MvtFeature {
    var type = 0
    var kind = ""
    var name = ""
    var geometry = -1 to -1
    var tags = -1 to -1
    val reader = Reader(data, start, end)

    while (reader.pos < end) {
        val tag = reader.int()
        when (tag ushr 3) {
            3 -> type = reader.int()
            2 -> reader.chunk { s, e -> tags = s to e }
            4 -> reader.chunk { s, e -> geometry = s to e }
            else -> reader.skip(tag and 7)
        }
    }

    if (tags.first >= 0) {
        val reading = Reader(data, tags.first, tags.second)
        while (reading.pos < reading.end) {
            val key = reading.int()
            val value = reading.int()
            when (keys.getOrNull(key)) {
                "class" -> kind = values.getOrNull(value).orEmpty()
                "name" -> name = values.getOrNull(value).orEmpty()
            }
        }
    }
    val rings = if (geometry.first >= 0) rings(Reader(data, geometry.first, geometry.second)) else emptyList()
    return MvtFeature(type, kind, name, rings)
}

/** Geometry is a command stream: move-to starts a ring, line-to extends it, close-path closes it. */
private fun rings(reader: Reader): List<FloatArray> {
    val rings = mutableListOf<FloatArray>()
    val ring = Points()
    var x = 0f
    var y = 0f
    while (reader.pos < reader.end) {
        val header = reader.int()
        val command = header and 0x7
        repeat(header ushr 3) {
            when (command) {
                1, 2 -> {
                    x += zigzag(reader.int())
                    y += zigzag(reader.int())
                    if (command == 1) ring.take()?.let(rings::add)
                    ring.add(x, y)
                }
                7 -> ring.closeBackToStart()
            }
        }
    }
    ring.take()?.let(rings::add)
    return rings
}

/** A ring being read: points appended flat, and handed over as one array when the ring ends. */
private class Points {
    private var points = FloatArray(64)
    private var size = 0

    fun add(x: Float, y: Float) {
        if (size + 2 > points.size) points = points.copyOf(points.size * 2)
        points[size++] = x
        points[size++] = y
    }

    fun closeBackToStart() {
        if (size > 2) add(points[0], points[1])
    }

    /** The ring so far, or null if it holds no points. Resets either way. A single point is a
     * ring here: that is what a place label is. */
    fun take(): FloatArray? {
        val ring = if (size >= 2) points.copyOf(size) else null
        size = 0
        return ring
    }
}

private fun zigzag(n: Int) = ((n shr 1) xor -(n and 1)).toFloat()

/** A tag value; only strings are drawn, so numbers come back as text and booleans as-is. */
private fun value(data: ByteArray, start: Int, end: Int): String {
    val reader = Reader(data, start, end)
    while (reader.pos < end) {
        val tag = reader.int()
        return when (tag ushr 3) {
            1 -> reader.string()
            4, 5 -> reader.int().toString()
            6 -> zigzag(reader.int()).toInt().toString()
            7 -> (reader.int() != 0).toString()
            else -> { reader.skip(tag and 7); continue }
        }
    }
    return ""
}

/** Just enough protobuf: varints, length-delimited chunks, and skipping what we don't read. */
private class Reader(val data: ByteArray, var pos: Int, val end: Int) {

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = data[pos++].toInt()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
    }

    fun int() = varint().toInt()

    fun string(): String {
        val length = int()
        return data.decodeToString(pos, pos + length).also { pos += length }
    }

    /** Reads a length-delimited chunk, hands its bounds to [body], and skips past it. */
    inline fun chunk(body: (start: Int, end: Int) -> Unit) {
        val length = int()
        val end = pos + length
        body(pos, end)
        pos = end
    }

    fun skip(wire: Int) {
        when (wire) {
            0 -> varint()
            1 -> pos += 8
            2 -> pos += int()
            5 -> pos += 4
            else -> error("unknown wire type $wire")
        }
    }
}
