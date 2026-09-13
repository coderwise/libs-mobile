package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.coderwise.libs.mapview.tiles.vector.mvt.GeometryType
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtFeature
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtLayer

/**
 * A tile's geometry pre-flattened into a few merged paths, ready to draw with a scale transform.
 *
 * Immutable once built, and says so: a slot holding one is then a composable that can be skipped,
 * so a recomposition that reaches it neither rebuilds its draw lambda nor invalidates its drawing.
 */
@Immutable
class RenderTile internal constructor(internal val ops: List<TileDrawOp>)

sealed interface TileDrawOp
private class FillOp(val path: Path, val color: Color) : TileDrawOp
private class StrokeOp(
    val path: Path,
    val color: Color,
    val width: ZoomWidth,
    val cap: StrokeCap,
    val join: StrokeJoin,
    /** Zoom below which this stroke isn't drawn at all — see [LayerStyle.strokeMinZoom]. */
    val minZoom: Int,
    /**
     * Zoom from which this stroke isn't drawn any more (exclusive). Only the coarse line a
     * casing-bearing paint draws at low zoom has one — it and the casing/stroke pair are the same
     * road painted two ways, and the band each owns is what keeps them from being drawn together.
     * See [LayerStyle.casingMinZoom].
     */
    val maxZoom: Int = Int.MAX_VALUE
) : TileDrawOp

/**
 * Flattens decoded [MvtLayer]s into merged [Path]s in normalised 0..1 tile coordinates,
 * one fill and one stroke path per styled layer (far fewer `drawPath` calls than per-feature).
 * Done once when a tile is decoded; the per-frame [drawRenderTile] only re-scales these paths.
 */
fun buildRenderTile(layers: List<MvtLayer>, style: VectorStyle): RenderTile {
    // Group each styled layer's ops into a fixed paint-rank bucket as we go, then emit buckets in
    // order — i.e. a canonical paint order independent of the tile's own layer order, which varies by
    // source (OpenFreeMap emits layers alphabetically, so `water` sits after roads and `boundary`
    // under everything; Evolyn uses yet another order). Bucketing reorders without the per-layer
    // `Pair` + `sortedBy` allocation an explicit sort would add to every tile decode.
    val buckets = arrayOfNulls<ArrayList<TileDrawOp>>(PAINT_RANK_COUNT)
    layers.forEach { layer ->
        if (layer.extent <= 0) return@forEach
        val inv = 1f / layer.extent

        // One merged path pair per distinct paint used in this layer. Usually that is exactly one —
        // but `transportation` carries ferries as well as roads, and they cannot share a path
        // because they are not drawn alike.
        val paints = LinkedHashMap<LayerStyle, LayerPaths>()

        layer.features.forEach { feature ->
            val ls = style.paint(layer.name, feature.featureClass)
                ?: when (feature.type) {
                    GeometryType.POLYGON -> style.polygonFallback
                    GeometryType.LINE -> style.lineFallback
                    else -> null
                }
                ?: return@forEach
            val paths = paints.getOrPut(ls) { LayerPaths() }
            when (feature.type) {
                // A polygon is an area, and is painted only by a paint that says what an area
                // looks like — a fill. Its stroke, if it has one, is then that area's outline,
                // built from this same path rather than a second copy of the same rings: another
                // copy would double the path memory of the densest layer on the map, which is the
                // one being outlined.
                //
                // A paint with a stroke and no fill is a line paint, and a polygon takes nothing
                // from it. OpenMapTiles files pedestrian plazas and station platforms among the
                // roads, and tracing their rings with a road's paint fenced each one off behind a
                // phantom street.
                GeometryType.POLYGON ->
                    if (ls.fill != null) feature.appendNormalized(paths.fill(), inv, close = true)

                GeometryType.LINE ->
                    if (ls.stroke != null) feature.appendNormalized(paths.stroke(), inv, close = false)

                else -> Unit
            }
        }
        if (paints.isEmpty()) return@forEach

        // Append into this layer's rank bucket (fill below stroke); buckets keep insertion order, so
        // layers sharing a rank fall back to tile order. Within one layer the lighter paint goes
        // down first, so a motorway draws over the side street it meets rather than under it, and a
        // road crosses over a ferry line rather than being cut by it. Ranked by the wide end of each
        // ramp, which is a property of the paint — the order must not change from zoom to zoom.
        val bucket = buckets[paintRank(layer.name)]
            ?: ArrayList<TileDrawOp>(2).also { buckets[paintRank(layer.name)] = it }
        val ordered = paints.entries
            .sortedBy { (paint, _) -> maxOf(paint.strokeWidth.widest, paint.casingWidth.widest).value }

        // Three passes over the layer's paints, not one pass per paint. The casing is the same
        // geometry drawn wider, underneath, so every casing in the layer has to be down before any
        // road is drawn over it: with one paint per road class, drawing each class's casing and
        // road together would let a motorway's dark edge cut straight across the residential
        // street it meets, instead of the street running under it.
        ordered.forEach { (paint, paths) ->
            paths.fillPath?.let { path -> paint.fill?.let { bucket.add(FillOp(path, it)) } }
        }
        ordered.forEach { (paint, paths) ->
            paths.strokePath?.let { path ->
                paint.casing?.let {
                    bucket.add(
                        StrokeOp(path, it, paint.casingWidth, paint.cap, paint.join, paint.lineMinZoom())
                    )
                }
                // The same road, below the zoom where a casing is worth drawing: one line in the
                // casing's colour, standing in for both passes. Emitted here rather than with the
                // strokes so it sits where the casing it replaces would have — nothing else in the
                // layer draws in its band, so the position only has to be defensible, not load-bearing.
                paint.coarseLine()?.let { color ->
                    bucket.add(
                        StrokeOp(
                            path, color, paint.strokeWidth, paint.cap, paint.join,
                            minZoom = paint.strokeMinZoom, maxZoom = paint.casingMinZoom
                        )
                    )
                }
            }
        }
        ordered.forEach { (paint, paths) ->
            paint.stroke?.let { color ->
                fun stroke(path: Path) = bucket.add(
                    StrokeOp(path, color, paint.strokeWidth, paint.cap, paint.join, paint.lineMinZoom())
                )
                // The lines, and then the outline of anything filled — which shares the fill's path.
                paths.strokePath?.let(::stroke)
                paths.fillPath?.let(::stroke)
            }
        }
    }

    val ops = ArrayList<TileDrawOp>()
    for (bucket in buckets) bucket?.let { ops.addAll(it) }
    return RenderTile(ops)
}

/**
 * Zoom from which this paint's casing-and-stroke pair draws — its own gate, or the zoom below which
 * the pair gives way to a single [LayerStyle.coarseLine], whichever is higher.
 */
private fun LayerStyle.lineMinZoom(): Int =
    if (casing != null) maxOf(strokeMinZoom, casingMinZoom) else strokeMinZoom

/**
 * The colour this paint draws as one line below [LayerStyle.casingMinZoom], or null if it has no
 * such band — a paint with no casing has nothing to collapse, and one that keeps its casing at
 * every zoom never leaves the band unclaimed.
 */
private fun LayerStyle.coarseLine(): Color? =
    if (casing == null || casingMinZoom <= strokeMinZoom) null else coarse ?: casing

/**
 * The merged geometry drawn with one paint: everything filled, and everything stroked as a line.
 * A filled polygon's outline is stroked from [fillPath] rather than copied into [strokePath].
 */
private class LayerPaths {
    var fillPath: Path? = null
        private set
    var strokePath: Path? = null
        private set

    fun fill(): Path = fillPath ?: Path().also { fillPath = it }
    fun stroke(): Path = strokePath ?: Path().also { strokePath = it }
}

/**
 * Canonical paint order (bottom to top) for the OMT layers this style paints, used instead of the
 * tile's own layer order (which varies by source). Unlisted layers (the geometry-type fallbacks) get
 * [DEFAULT_PAINT_RANK], just below water, so an unrecognised area fill can never hide it.
 */
private val PAINT_ORDER: Map<String, Int> = listOf(
    // Land use under land cover, not over it: a wood inside a residential area is still a wood,
    // and the built-up tint drawn on top of it would grey it out.
    "landuse", "landcover", "park", "aeroway",
    "water", "waterway",
    "building",
    "transportation_casing", "transportation",
    "boundary",
).withIndex().associate { (i, name) -> name to i }

private val DEFAULT_PAINT_RANK: Int = PAINT_ORDER.getValue("water") - 1
private val PAINT_RANK_COUNT: Int = PAINT_ORDER.size

private fun paintRank(name: String): Int = PAINT_ORDER[name] ?: DEFAULT_PAINT_RANK

/** Appends a feature's parts to [path] in normalised 0..1 coordinates (multiply tile coords by [inv]). */
private fun MvtFeature.appendNormalized(path: Path, inv: Float, close: Boolean) {
    val coords = geometry.coords
    for (k in 0 until geometry.partCount) {
        val start = geometry.partStarts[k]
        val end = geometry.partEnd(k)
        for (v in start until end) {
            val x = coords[2 * v] * inv
            val y = coords[2 * v + 1] * inv
            if (v == start) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (close) path.close()
    }
}

/**
 * Draws the [src] fraction of a pre-flattened [RenderTile] into this slot.
 *
 * Normalised paths are scaled to the slot's pixel box, expanded by [TILE_BLEED] on every side so
 * fills fully cover the boundary column — otherwise antialiased edges leave a hairline seam at
 * every tile border. The canvas clips the overflow.
 *
 * Stroke widths are resolved against [zoom] and the display, then divided back out of the scale, so
 * a road is the same thickness to the eye on any screen and a motorway is a hairline at z8 and a
 * ribbon at z17 ([ZoomWidth]). [zoom] is the zoom of the cell being drawn, not of the geometry, so
 * an over-zoomed ancestor's roads are drawn at the width the view is asking for.
 *
 * [src] is a fraction of the tile in `0..1`: the whole of it for a tile at its own zoom, and a
 * smaller square of an ancestor's when the view has zoomed past what the source carries.
 */
fun DrawScope.drawRenderTile(
    renderTile: RenderTile,
    background: Color,
    zoom: Int,
    src: Rect,
) {
    clipRect {
        drawRect(color = background)
        if (renderTile.ops.isEmpty() || src.width <= 0f || src.height <= 0f) return@clipRect

        val scaleX = size.width + TILE_BLEED * 2
        val scaleY = size.height + TILE_BLEED * 2
        val widthScale = strokeWidthScale(scaleX, scaleY, src)
        val matrix = srcMatrix(scaleX, scaleY, src)
        withTransform({ transform(matrix) }) {
            renderTile.ops.forEach { op ->
                when (op) {
                    is FillOp -> drawPath(op.path, color = op.color)
                    // A gated stroke is skipped outright, not drawn thin: the point is not to pay
                    // for tracing the geometry at all (see [LayerStyle.strokeMinZoom]).
                    is StrokeOp -> if (zoom >= op.minZoom && zoom < op.maxZoom) drawPath(
                        op.path,
                        color = op.color,
                        // The stroke is drawn inside a transform scaled by widthScale, so divide
                        // it back out: op.width.at(zoom).toPx() is what lands on the screen.
                        style = Stroke(
                            width = op.width.at(zoom).toPx() / widthScale,
                            cap = op.cap,
                            join = op.join
                        )
                    )
                }
            }
        }
    }
}

/**
 * The transform that puts the [src] fraction of a tile's normalised paths where the slot wants them.
 *
 * Applied to a point, the calls compose back to front: blow the tile's `[0,1]` coordinates up so
 * that [src] is a unit square, slide [src]'s corner down to the origin, scale to the bled slot box,
 * and shift by the bleed. So [src] — and only it — covers the box, and the rest of the tile lands
 * outside it for [clipRect] to drop. A full-tile [src] is the identity on the first two steps,
 * which is what a tile at its own zoom draws through.
 *
 * Extracted from [drawRenderTile] so it can be checked without a canvas, the same way
 * [strokeWidthScale] is: an over-zoomed cell that lands off its own box draws nothing at all, and
 * on screen that is indistinguishable from a tile that never arrived.
 */
internal fun srcMatrix(scaleX: Float, scaleY: Float, src: Rect): Matrix = Matrix().apply {
    translate(-TILE_BLEED, -TILE_BLEED)
    scale(scaleX, scaleY)
    translate(-src.left / src.width, -src.top / src.height)
    scale(1f / src.width, 1f / src.height)
}

/**
 * The total scale a stroke width is divided by before being handed to [Stroke], so that what lands
 * on screen is the width the style asked for.
 *
 * Both scales in the draw transform count: the slot box ([scaleX]/[scaleY]), and the magnification
 * an over-zoomed ancestor's normalised paths are blown up by, which is `1/src.width`. Only the box
 * scale used to be divided out, so an over-zoomed road came out that many times too thick — four
 * times at z16 and eight at z17 off a z14 source, which is every vector source shipped.
 */
internal fun strokeWidthScale(scaleX: Float, scaleY: Float, src: Rect): Float =
    (scaleX + scaleY) / 2f * (2f / (src.width + src.height))

/** Sub-pixel overdraw on each slot edge to hide antialiased seams between adjacent tiles. */
internal const val TILE_BLEED = 0.5f
