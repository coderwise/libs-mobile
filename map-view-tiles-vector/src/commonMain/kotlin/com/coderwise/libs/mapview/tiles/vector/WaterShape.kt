package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.ui.graphics.Path
import com.coderwise.libs.mapview.tiles.vector.mvt.GeometryType
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtLayer
import com.coderwise.libs.mapview.tiles.vector.mvt.decodeMvt

/**
 * The water in [bytes] as one merged [Path] in normalised `0..1` tile coordinates, or null if the
 * tile is entirely dry — or could not be read.
 *
 * Read straight off the vector tile rather than approximated, because the thing that makes a
 * coastline worth having is that it is the real one. A caller drawing something of its own over the
 * sea — a coverage overlay that should not cover it, say — wants the shape rather than the paint,
 * and scales this path to whatever size it is drawing the tile at.
 *
 * The same shape reaches a drawn tile as [VectorTile.water]; this is the way to it for a caller
 * that wants only the coastline, and decodes only [WATER_LAYER] to get there.
 */
fun waterPath(bytes: ByteArray): Path? {
    val layers = runCatching { decodeMvt(bytes, keep = setOf(WATER_LAYER)) }.getOrNull() ?: return null
    return waterPath(layers)
}

/**
 * As [waterPath], for a tile that has already been decoded.
 *
 * Only [WATER_LAYER] and only its polygons: rivers and streams also arrive as lines in `waterway`,
 * and a line has no inside, so it would contribute an infinitely thin nothing to an area. The
 * polygons here are the seas, the lakes and the wider rivers — everything with a surface.
 *
 * Ring winding is left exactly as the tile encodes it (exterior one way, holes the other) and the
 * path keeps the default non-zero fill, so an island in a lake stays dry.
 */
internal fun waterPath(layers: List<MvtLayer>): Path? {
    val path = Path()
    var any = false
    layers.forEach { layer ->
        if (layer.name != WATER_LAYER || layer.extent <= 0) return@forEach
        val inv = 1f / layer.extent
        layer.features.forEach { feature ->
            if (feature.type != GeometryType.POLYGON) return@forEach
            val geometry = feature.geometry
            for (part in 0 until geometry.partCount) {
                val start = geometry.partStarts[part]
                val end = geometry.partEnd(part)
                if (end - start < MIN_RING_VERTICES) continue
                for (v in start until end) {
                    val x = geometry.coords[2 * v] * inv
                    val y = geometry.coords[2 * v + 1] * inv
                    if (v == start) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                any = true
            }
        }
    }
    return path.takeIf { any }
}

/** The MVT layer holding water with a surface — oceans, lakes, and the broader rivers. */
private const val WATER_LAYER = "water"

/** Fewer vertices than this cannot enclose anything, so the ring is dropped rather than closed. */
private const val MIN_RING_VERTICES = 3
