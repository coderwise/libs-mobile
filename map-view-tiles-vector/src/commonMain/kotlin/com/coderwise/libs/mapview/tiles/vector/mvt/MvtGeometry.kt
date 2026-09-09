package com.coderwise.libs.mapview.tiles.vector.mvt

/**
 * A feature's geometry as flat primitive arrays — no per-vertex object boxing.
 *
 * [coords] interleaves `x, y` tile-local pairs for every vertex across all parts, so vertex `v`
 * is at `coords[2v], coords[2v + 1]`. [partStarts] holds the vertex index where each part begins;
 * part `k` spans vertices `partStarts[k] until partEnd(k)`. A "part" is one line, one polygon ring,
 * or the single accumulated run of a (multi)point feature.
 *
 * Replaces the old `List<List<TilePoint>>`: a dense tile has thousands of vertices, and a boxed
 * `TilePoint` plus nested `ArrayList`s per vertex was the bulk of the decode-time garbage.
 */
class TileGeometry(val coords: IntArray, val partStarts: IntArray) {
    val partCount: Int get() = partStarts.size
    val vertexCount: Int get() = coords.size / 2

    /** Exclusive vertex index where part [k] ends. */
    fun partEnd(k: Int): Int = if (k + 1 < partStarts.size) partStarts[k + 1] else vertexCount

    companion object {
        val EMPTY = TileGeometry(IntArray(0), IntArray(0))
    }
}

/**
 * Decodes an MVT feature `geometry` command stream into a [TileGeometry].
 *
 * The stream is a flat array of integers interpreted as commands
 * (`CommandInteger = (id << 3) | count`) followed by their parameters
 * (`ParameterInteger`, zig-zag encoded). Coordinates are cursor-relative deltas in
 * tile-local units (0..extent). See the MVT 2.1 spec §4.3.
 *
 * Each MoveTo begins a new part:
 * - POINT features yield one part holding every point.
 * - LINESTRING / POLYGON features yield one part per line / ring.
 *
 * Malformed streams (truncated parameters, out-of-order commands) are tolerated by
 * stopping early rather than throwing — a partial tile still renders.
 */
internal fun decodeGeometry(type: GeometryType, geometry: IntArray): TileGeometry =
    decodeGeometry(type, geometry, geometry.size)

/**
 * As [decodeGeometry], but reads only the first [size] values of [geometry]. Lets the tile parser
 * hand its growable command-stream buffer straight in (backing array + logical length) without first
 * copying it to an exact-sized array — one fewer large allocation per feature while panning.
 */
internal fun decodeGeometry(type: GeometryType, geometry: IntArray, size: Int): TileGeometry {
    if (size == 0) return TileGeometry.EMPTY

    // coords needs at most one int per stream value (close-path may add a few more, which grow);
    // size to the stream length up front so the common case never reallocates.
    val coords = IntBag(size)
    val partStarts = IntBag(4)
    val isPoint = type == GeometryType.POINT
    var pointPartOpen = false
    var cursorX = 0
    var cursorY = 0
    var i = 0

    while (i < size) {
        val command = geometry[i++]
        val id = command and 0x7
        val count = command ushr 3

        when (id) {
            CMD_MOVE_TO -> {
                var n = 0
                while (n < count) {
                    if (i + 1 >= size) return TileGeometry(coords.toArray(), partStarts.toArray())
                    cursorX += zigzag(geometry[i++])
                    cursorY += zigzag(geometry[i++])
                    // A MoveTo starts a new part for lines/polygons. For points, keep accumulating
                    // into a single part so a multipoint stays together.
                    if (isPoint) {
                        if (!pointPartOpen) {
                            partStarts.add(coords.size / 2)
                            pointPartOpen = true
                        }
                    } else {
                        partStarts.add(coords.size / 2)
                    }
                    coords.add(cursorX)
                    coords.add(cursorY)
                    n++
                }
            }

            CMD_LINE_TO -> {
                var n = 0
                while (n < count) {
                    if (i + 1 >= size) return TileGeometry(coords.toArray(), partStarts.toArray())
                    cursorX += zigzag(geometry[i++])
                    cursorY += zigzag(geometry[i++])
                    coords.add(cursorX)
                    coords.add(cursorY)
                    n++
                }
            }

            CMD_CLOSE_PATH -> {
                // Close the ring back to its first vertex so polygon fills are well-formed.
                if (partStarts.size > 0) {
                    val firstVertex = partStarts[partStarts.size - 1]
                    if (coords.size / 2 > firstVertex) {
                        coords.add(coords[2 * firstVertex])
                        coords.add(coords[2 * firstVertex + 1])
                    }
                }
            }

            else -> return TileGeometry(coords.toArray(), partStarts.toArray()) // unknown id — stop defensively
        }
    }
    return TileGeometry(coords.toArray(), partStarts.toArray())
}

/** A minimal growable primitive int buffer — avoids the boxing a `MutableList<Int>` would incur. */
internal class IntBag(initialCapacity: Int) {
    /** The backing store; valid entries are `[0, size)`. Exposed so callers can read without copying. */
    var data = IntArray(if (initialCapacity < 4) 4 else initialCapacity)
        private set
    var size: Int = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    operator fun get(index: Int): Int = data[index]

    fun toArray(): IntArray = data.copyOf(size)
}

/** Decodes a zig-zag encoded parameter integer back to a signed delta. */
private fun zigzag(value: Int): Int = (value ushr 1) xor -(value and 1)

private const val CMD_MOVE_TO = 1
private const val CMD_LINE_TO = 2
private const val CMD_CLOSE_PATH = 7
