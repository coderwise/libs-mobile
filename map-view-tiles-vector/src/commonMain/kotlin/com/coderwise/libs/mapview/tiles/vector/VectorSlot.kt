package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtLayer
import com.coderwise.libs.mapview.tiles.vector.mvt.decodeMvt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Decodes a vector tile into the paths that draw it, in the colours [style] asks for.
 *
 * Costs tens of milliseconds and belongs off the main thread — and, better still, upstream of the
 * view entirely, so that a tile is decoded once when it arrives rather than whenever a slot happens
 * to want it.
 *
 * Widths are not baked in: they are [ZoomWidth]s resolved against the zoom the slot is drawing at,
 * so one decoded tile serves every zoom it is stretched over. [zoom] is the level the tile was cut
 * at, which is what the labels gate on.
 */
fun decodeVectorTile(
    zoom: Int,
    bytes: ByteArray,
    style: VectorStyle = VectorStyle()
): VectorTile? = runCatching {
    val layers = decodeMvt(
        bytes,
        keep = DRAWN_LAYERS,
        classLayers = CLASS_LAYERS,
        nameLayers = LABELLED_LAYERS
    )
    VectorTile(
        render = buildRenderTile(layers, style),
        labels = labelsOf(layers, style),
        background = style.background,
        ink = style.ink,
        halo = style.halo,
        water = waterPath(layers),
        zoom = zoom
    )
}.getOrNull()

/**
 * One slot's worth of vector tile: draws the [src] fraction of an already-decoded [tile].
 *
 * Paths outlive recomposition, so panning and zooming is a transform and a handful of `drawPath`
 * calls however far the tile is magnified. Line widths are applied at draw time, so a road stays
 * the same width on screen instead of fattening with the magnification.
 *
 * [zoom] is the zoom of the cell being drawn — hand it `shown.key.z`. It is what the widths and the
 * zoom gates are resolved against, so an over-zoomed ancestor's roads come out at the width the
 * view is asking for rather than the width its own level wanted.
 */
@Composable
fun VectorSlot(
    tile: VectorTile,
    src: Rect,
    zoom: Int = tile.zoom,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    cache: () -> Boolean = { false }
) = VectorSlot(tile.render, tile.background, src, zoom, modifier, alpha, cache)

/**
 * The same, for a caller holding its own decoded tile: everything here needs is the paths and the
 * colour behind them.
 *
 * Each tile draws into a graphics layer of its own, and that is what makes a pan cheap: the draw
 * list is captured once, so moving the grid moves a layer instead of re-tracing every stroke of
 * every tile on every frame.
 *
 * [cache] goes one step further and asks for a *compositing* layer — the geometry is rasterised
 * into a texture once and the texture reused until the drawing changes, so a pan blends textures
 * rather than replaying a display list. It costs a texture per visible tile in graphics memory, and
 * it is **not** an unqualified win: measured on a dense city centre it cuts janky frames on a slow
 * drag (37.7% against 96.6% without) and makes them far worse on a fling (35.3% against 4.4%, and
 * a third of the frames delivered), because a fling changes what is on screen faster than a texture
 * can be reused. Hence a lambda rather than a flag: it is read in the draw phase, so a caller can
 * answer it per frame from what the gesture is doing without recomposing the grid.
 *
 * [alpha] is applied as a layer property, not per draw op, so fading a tile in does not invalidate
 * its drawing. Without [cache] the alpha is what the layer is for, and it modulates instead.
 */
@Composable
fun VectorSlot(
    render: RenderTile,
    background: Color,
    src: Rect,
    zoom: Int,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    cache: () -> Boolean = { false }
) {
    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                // Read here rather than in composition: this block runs in the draw phase, so a
                // caller that turns the cache off mid-gesture updates a layer property instead of
                // recomposing every tile on screen.
                compositingStrategy =
                    if (cache()) CompositingStrategy.Offscreen else CompositingStrategy.ModulateAlpha
            }
            .drawBehind { drawRenderTile(render, background, zoom, src) }
    )
}

/** Everything the style paints or labels; the rest of the tile is never parsed into features. */
private val DRAWN_LAYERS = setOf(
    "landcover", "landuse", "park", "water", "waterway", "aeroway", "building", "boundary",
    "transportation", "transportation_casing", "place", "transportation_name"
)

/** The layers whose features carry a name worth drawing. */
private val LABELLED_LAYERS = setOf("place", "transportation_name")

/** What each layer is styled by — `class` for most, `admin_level` for boundaries. */
private val CLASS_LAYERS = VectorStyle.CLASS_AWARE_LAYERS + mapOf("place" to "class")

/** A decoded tile: everything about it that drawing needs, and nothing that loading did. */
class VectorTile internal constructor(
    internal val render: RenderTile,
    internal val labels: List<Label>,
    internal val background: Color,
    internal val ink: Color,
    internal val halo: Color,
    /**
     * The tile's water as one path in `0..1` tile coordinates, or null where the tile is dry.
     *
     * Kept apart from the drawing because it answers a different question: not "what colour is
     * this" but "where does the land stop". A caller drawing something of its own over the sea — a
     * coverage overlay that should not cover it, say — needs the shape rather than the paint, and
     * reading it off the tile is what makes it the real coastline instead of an approximation.
     */
    val water: Path?,
    /** The zoom the tile was cut at — what a slot draws it as unless it says otherwise. */
    val zoom: Int
)

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
            val anchor = feature.geometry.anchorVertex() ?: return@mapNotNull null
            val size = style.place(feature.featureClass.orEmpty()) ?: return@mapNotNull null
            val text = feature.name?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // As a fraction of the tile, so drawing them needs nothing but the tile's own box.
            Label(
                feature.geometry.coords[2 * anchor].toFloat() / layer.extent,
                feature.geometry.coords[2 * anchor + 1].toFloat() / layer.extent,
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
        if (point - start > 1 && abs(wrapToPi(heading - sofar)) > TURN) {
            best = longer(best, runOf(ring, start, point - 1))
            start = point - 1
        }
    }
    return longer(best, runOf(ring, start, points - 1))
}

private const val TURN = 0.3f // radians, about 17 degrees

private fun heading(ring: FloatArray, from: Int, to: Int) =
    atan2(ring[2 * to + 1] - ring[2 * from + 1], ring[2 * to] - ring[2 * from])

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


