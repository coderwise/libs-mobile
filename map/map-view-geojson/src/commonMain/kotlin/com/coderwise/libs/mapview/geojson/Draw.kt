package com.coderwise.libs.mapview.geojson

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.coderwise.libs.mapview.MapOverlayScope

/**
 * What drawing GeoJSON needs of a map, which is the part of [MapOverlayScope] that a map not on
 * screen can have too: a static image draws through its own, with the zoom it was made at.
 */
interface GeoJsonProjection {
    /** The zoom the map is at, which [drawGeoJson] compares with its `minZoom`. */
    val zoom: Float

    /**
     * Appends a subpath to [path] for [world], a shape in unit Mercator x and y pairs, as it
     * stands on the map: [MapOverlayScope.trace]. Every call starts with its own `moveTo`, and each
     * step along the shape is taken the short way round the world.
     */
    fun trace(path: Path, world: DoubleArray)
}

/**
 * Draws [data] in [style] through [projection], with the density and the size of this scope: the
 * same drawing [GeoJson] puts on a live map, for a map that is not one — an image made without a
 * composition. Draws nothing while [projection]'s zoom is under [minZoom].
 *
 * Polygons first, in order, each with its holes; then lines; then points, so that a point is never
 * lost under the shape it is on. It projects all of [data] each time it is called, which a single
 * image does not mind; a map that redraws every frame is what [GeoJson] is for.
 */
fun DrawScope.drawGeoJson(
    data: FeatureCollection,
    style: GeoJsonStyle,
    projection: GeoJsonProjection,
    minZoom: Float = 0f
) {
    if (shownAt(projection.zoom, minZoom)) drawShapes(data.toShapes(), style, projection, Scratch())
}

/** Whether a layer with [minZoom] is drawn, and can be touched, at [zoom]. */
internal fun shownAt(zoom: Float, minZoom: Float) = zoom >= minZoom

/**
 * The paths a layer draws through, kept from frame to frame so that drawing allocates none.
 *
 * One path holds all the lines, and one all the points; the polygons have a path each in turn,
 * because the even-odd rule that cuts out a hole would also cut out the place two separate
 * polygons overlap.
 */
internal class Scratch {
    val area = Path()
    val lines = Path()
    val dots = Path()
    val dot = DoubleArray(4)
}

internal fun DrawScope.drawShapes(
    shapes: Shapes,
    style: GeoJsonStyle,
    projection: GeoJsonProjection,
    scratch: Scratch
) {
    val width = style.strokeWidth.toPx()
    val outline = style.stroke.alpha > 0f && width > 0f
    val line = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val area = scratch.area
    for (feature in shapes.features) {
        if (feature.polygons.isEmpty()) continue
        area.rewind()
        area.fillType = PathFillType.EvenOdd // after the rewind, which not every platform keeps it through
        for (rings in feature.polygons) {
            for (ring in rings) {
                projection.trace(area, ring)
                area.close()
            }
        }
        if (style.fill.alpha > 0f) drawPath(area, style.fill)
        if (outline) drawPath(area, style.stroke, style = line)
    }

    val lines = scratch.lines
    lines.rewind()
    for (feature in shapes.features) for (world in feature.lines) projection.trace(lines, world)
    if (outline && !lines.isEmpty) drawPath(lines, style.stroke, style = line)

    // A point is a line of no length, which a round cap makes a dot: the same trace as the rest,
    // with its antimeridian handling and its one draw call for any number of points.
    val dots = scratch.dots
    val dot = scratch.dot
    dots.rewind()
    for (feature in shapes.features) {
        val points = feature.points
        for (i in points.indices step 2) {
            dot[0] = points[i]
            dot[1] = points[i + 1]
            dot[2] = dot[0]
            dot[3] = dot[1]
            projection.trace(dots, dot)
        }
    }
    val radius = style.pointRadius.toPx()
    if (style.pointColor.alpha > 0f && radius > 0f && !dots.isEmpty) {
        drawPath(dots, style.pointColor, style = Stroke(radius * 2, cap = StrokeCap.Round))
    }
}
