package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.coderwise.libs.mapview.tiles.vector.mvt.GeometryType
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtFeature
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtLayer
import com.coderwise.libs.mapview.tiles.vector.mvt.TileGeometry
import com.coderwise.libs.mapview.tiles.vector.mvt.decodeMvt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Decodes a vector tile into the paths that draw it, in the colours and widths [style] asks for.
 * Costs tens of milliseconds and belongs off the main thread — and, better still, upstream of the
 * view entirely, so that a tile is decoded once when it arrives rather than whenever a slot
 * happens to want it. [zoom] is the level the tile was cut at, which is what the styling thins
 * lines by: a 6 dp motorway is right over a city and a smear over a continent.
 */
fun decodeVectorTile(
    zoom: Int,
    bytes: ByteArray,
    style: VectorStyle = VectorStyle()
): VectorTile? = runCatching {
    drawingOf(
        zoom,
        decodeMvt(
            bytes,
            keep = DRAWN_LAYERS,
            classLayers = CLASS_LAYERS,
            nameLayers = LABELLED_LAYERS
        ),
        style
    )
}.getOrNull()

/**
 * One slot's worth of vector tile: draws the [src] fraction of an already-decoded [tile]. Land
 * use, water and roads only, for now.
 *
 * Paths outlive recomposition, so panning and zooming is a transform and a handful of `drawPath`
 * calls however far the tile is magnified. Line widths are applied at draw time, so a road stays
 * the same width on screen instead of fattening with the magnification.
 */
@Composable
fun VectorSlot(tile: VectorTile, src: Rect, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val side = size.width / src.width
        // A tile carries a little geometry past its own edge, so a road crossing a seam joins up
        // with its other half. Trim it here: it is the geometry that has to stay inside the slot.
        clipRect {
            translate(-src.left * side, -src.top * side) {
                scale(side / tile.extent, pivot = Offset.Zero) {
                    tile.painted.forEach {
                        if (it.widthDp == null) {
                            drawPath(it.path, it.color)
                        } else {
                            // A stroke drawn under this transform is magnified with it, so undo
                            // that: a road is the same width on screen however far it is stretched.
                            val width = it.widthDp * density * tile.extent / side
                            drawPath(
                                it.path,
                                it.color,
                                style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val DRAWN_LAYERS = setOf(
    "landcover", "landuse", "water", "waterway", "transportation", "place", "transportation_name",
    "building"
)

/** The layers whose paint depends on what kind of thing a feature is. */
private val STYLED_LAYERS = setOf("landcover", "landuse", "waterway", "transportation", "place")

/** The layers whose features carry a name worth drawing. */
private val LABELLED_LAYERS = setOf("place", "transportation_name")

/** The tag that says what kind of thing a feature is. */
private const val CLASS_TAG = "class"

private val CLASS_LAYERS = STYLED_LAYERS.associateWith { CLASS_TAG }

/** Paths in tile coordinates: [widthDp] null fills, otherwise strokes that width on screen. */
internal class Painted(val path: Path, val color: Color, val widthDp: Float? = null)

/** A decoded tile: everything about it that drawing needs, and nothing that loading did. */
class VectorTile internal constructor(
    internal val extent: Float,
    internal val painted: List<Painted>,
    internal val labels: List<Label>,
    internal val ink: Color,
    internal val halo: Color,
    /**
     * The tile's water as one path in `0..1` tile coordinates, or null where the tile is dry.
     *
     * Kept apart from [painted] because it answers a different question: not "what colour is this"
     * but "where does the land stop". A caller drawing something of its own over the sea — a
     * coverage overlay that should not cover it, say — needs the shape rather than the paint, and
     * reading it off the tile is what makes it the real coastline instead of an approximation.
     *
     * Only `water`, and only its polygons: rivers also arrive as lines in `waterway`, and a line
     * has no inside. Ring winding is left as the tile encodes it and the path keeps the default
     * non-zero fill, so an island in a lake stays dry.
     */
    val water: Path?
)

private fun drawingOf(z: Int, layers: List<MvtLayer>, style: VectorStyle): VectorTile {
    val extent = (layers.firstOrNull()?.extent ?: 4096).toFloat()
    val painted = mutableListOf<Painted>()

    // Ground first, then water over it, then roads over that.
    painted += Painted(Path().apply { addRect(Rect(0f, 0f, extent, extent)) }, style.land)
    layers.filter { it.name == "landcover" || it.name == "landuse" }.forEach { layer ->
        layer.features.groupBy { style.landcover(it.featureClass.orEmpty()) }.forEach { (color, features) ->
            if (color != null) painted += Painted(pathOf(features), color)
        }
    }
    layers.filter { it.name == "water" }.forEach { layer ->
        painted += Painted(pathOf(layer.features), style.water)
    }
    layers.filter { it.name == "waterway" }.forEach { layer ->
        layer.features.groupBy { style.waterway(it.featureClass.orEmpty()) }.forEach { (width, features) ->
            if (width != null) painted += Painted(pathOf(features), style.water, width.narrowed(z))
        }
    }

    // Roads twice over: every casing first, then every surface, so a junction reads as a junction
    // instead of one road's outline cutting across another's surface.
    val roads = layers.filter { it.name == "transportation" }
        .flatMap { layer -> layer.features.groupBy { style.road(it.featureClass.orEmpty()) }.toList() }
        .mapNotNull { (road, features) -> road?.let { it to pathOf(features) } }
        .sortedBy { it.first.width }
    roads.forEach { (road, path) -> painted += Painted(path, road.casing, (road.width + 1f).narrowed(z)) }
    roads.forEach { (road, path) -> painted += Painted(path, road.color, road.width.narrowed(z)) }

    // Buildings last, over the streets they stand between. Filled always, outlined only where a
    // building is big enough for its own edge to be what separates it from its neighbour.
    layers.filter { it.name == "building" }.forEach { layer ->
        val path = pathOf(layer.features)
        style.building?.let { painted += Painted(path, it) }
        if (z >= BUILDING_OUTLINE_MIN_ZOOM) {
            // Thin: buildings arrive around z14 as specks, and an outline with any weight at that
            // size is all you would see.
            style.buildingOutline?.let { painted += Painted(path, it, BUILDING_OUTLINE_DP) }
        }
    }

    return VectorTile(extent, painted, labelsOf(layers, style), style.ink, style.halo, waterOf(layers))
}

/** The water polygons of [layers], normalised to `0..1`, or null where there are none. */
private fun waterOf(layers: List<MvtLayer>): Path? {
    var any = false
    val path = Path()
    layers.filter { it.name == "water" }.forEach { layer ->
        if (layer.extent <= 0) return@forEach
        val inv = 1f / layer.extent
        layer.features.forEach { feature ->
            if (feature.type != GeometryType.POLYGON) return@forEach
            val geometry = feature.geometry
            for (part in 0 until geometry.partCount) {
                val start = geometry.partStarts[part]
                val end = geometry.partEnd(part)
                // Fewer than three vertices cannot enclose anything.
                if (end - start < 3) continue
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

/**
 * What the tile has to say: places first, biggest first, then road names. That order is the whole
 * of the priority scheme, because a label that collides with one already placed is dropped.
 */
private fun labelsOf(layers: List<MvtLayer>, style: VectorStyle): List<Label> =
    placeLabels(layers, style).sortedByDescending { it.size } +
        (style.roadName?.let { roadLabels(layers, it) } ?: emptyList())

private fun placeLabels(layers: List<MvtLayer>, style: VectorStyle): List<Label> = layers
    .filter { it.name == "place" }
    .flatMap { layer ->
        layer.features.mapNotNull { feature ->
            if (feature.geometry.vertexCount == 0) return@mapNotNull null
            val size = style.place(feature.featureClass.orEmpty()) ?: return@mapNotNull null
            val text = feature.name?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // As a fraction of the tile, so drawing them needs nothing but the tile's own box.
            Label(
                feature.geometry.coords[0].toFloat() / layer.extent,
                feature.geometry.coords[1].toFloat() / layer.extent,
                text,
                size
            )
        }
    }

/**
 * A road name sits along its road, so each one is given the straightest run of it in this tile:
 * the middle of that run to sit on, its angle to turn by, and its length, which is how the layout
 * knows whether the name fits. One label per name per tile — a road arrives in pieces, split
 * wherever a tag changes, and they are all the same road to a reader.
 *
 * Longest run first, so where two names compete the one with more road to stand on wins.
 */
private fun roadLabels(layers: List<MvtLayer>, size: Float): List<Label> = layers
    .filter { it.name == "transportation_name" }
    .flatMap { layer ->
        layer.features
            .mapNotNull { feature -> feature.name?.takeIf { it.isNotEmpty() }?.let { it to feature } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (name, pieces) ->
                val run = pieces.flatMap { it.geometry.parts() }.mapNotNull(::straightRun)
                    .maxByOrNull { it.length } ?: return@mapNotNull null
                Label(
                    x = run.midX / layer.extent,
                    y = run.midY / layer.extent,
                    text = name,
                    size = size,
                    turn = run.turn,
                    upright = false,
                    room = run.length / layer.extent
                )
            }
    }
    .sortedByDescending { it.room }

/** The straightest stretch of a line: where a name can sit without following a bend. */
private class Run(val midX: Float, val midY: Float, val turn: Float, val length: Float)

/**
 * The longest run of [ring] that never strays more than [TURN] from the direction it set out in.
 * A road is drawn as dozens of short segments, so this is what stands in for "the straight part".
 */
private fun straightRun(ring: FloatArray): Run? {
    val points = ring.size / 2
    var best: Run? = null
    var start = 0

    for (point in 1..<points) {
        val heading = heading(ring, point - 1, point)
        val sofar = heading(ring, start, point)
        if (point - start > 1 && abs(wrapped(heading - sofar)) > TURN) {
            best = longer(best, runOf(ring, start, point - 1))
            start = point - 1
        }
    }
    return longer(best, runOf(ring, start, points - 1))
}

private const val TURN = 0.3f // radians, about 17 degrees

private fun heading(ring: FloatArray, from: Int, to: Int) =
    atan2(ring[2 * to + 1] - ring[2 * from + 1], ring[2 * to] - ring[2 * from])

/** An angle brought back into -pi..pi, so that 359 degrees off is one degree off. */
private fun wrapped(radians: Float) = radians - round(radians / (2 * PI.toFloat())) * 2 * PI.toFloat()

private fun runOf(ring: FloatArray, from: Int, to: Int): Run? {
    if (to <= from) return null
    val dx = ring[2 * to] - ring[2 * from]
    val dy = ring[2 * to + 1] - ring[2 * from + 1]
    // Upside-down text is unreadable, so a run heading left is read as the same line heading right.
    val degrees = atan2(dy, dx) * 180f / PI.toFloat()
    return Run(
        midX = (ring[2 * from] + ring[2 * to]) / 2,
        midY = (ring[2 * from + 1] + ring[2 * to + 1]) / 2,
        turn = if (degrees > 90f) degrees - 180f else if (degrees < -90f) degrees + 180f else degrees,
        length = sqrt(dx * dx + dy * dy)
    )
}

private fun longer(a: Run?, b: Run?) = if (a == null || (b != null && b.length > a.length)) b else a

private fun pathOf(features: List<MvtFeature>) = Path().apply {
    features.forEach { feature ->
        val geometry = feature.geometry
        for (part in 0 until geometry.partCount) {
            val start = geometry.partStarts[part]
            val end = geometry.partEnd(part)
            if (end <= start) continue
            moveTo(geometry.coords[2 * start].toFloat(), geometry.coords[2 * start + 1].toFloat())
            for (v in start + 1 until end) {
                lineTo(geometry.coords[2 * v].toFloat(), geometry.coords[2 * v + 1].toFloat())
            }
            if (feature.type == GeometryType.POLYGON) close()
        }
    }
}

/**
 * This geometry's parts as flat `x, y` runs — what the label maths works on, which reads a line's
 * shape rather than drawing it. Allocates, so it is for the once-per-tile label pass and not for
 * anything that runs per frame.
 */
private fun TileGeometry.parts(): List<FloatArray> = (0 until partCount).map { part ->
    val start = partStarts[part]
    val end = partEnd(part)
    FloatArray((end - start) * 2) { coords[2 * start + it].toFloat() }
}

/** How wide a building's outline is drawn, in dp. */
private const val BUILDING_OUTLINE_DP = 0.5f

/**
 * Lines thin out as the map zooms out: a 6 dp motorway is fine over a city and a smear over a
 * continent. Full width from zoom 14 up, then a fifth narrower per level, down to a hairline.
 */
private fun Float.narrowed(z: Int) =
    (this * 2f.pow(0.4f * (z - 14).coerceAtMost(0))).coerceAtLeast(0.35f)
