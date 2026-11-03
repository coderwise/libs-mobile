package com.coderwise.libs.mapview.geojson

import com.coderwise.libs.mapview.LatLon
import com.coderwise.libs.mapview.toWorld
import kotlin.math.round
import kotlin.math.sqrt

/**
 * A [FeatureCollection] in the form it is drawn and touched from: projected once, kept for as long
 * as the data stays the same. Only the affine is left to do on each frame, and for a touch not
 * even that — see [nearest].
 */
internal class Shapes(val features: List<ShapedFeature>)

/**
 * One feature's parts, in the form [toWorld] makes (unit Mercator x and y), with every
 * multi-geometry and collection flattened into the lists they come to.
 */
internal class ShapedFeature(
    val feature: Feature,
    /** Pairs of x and y, one per point. */
    val points: DoubleArray,
    val lines: List<DoubleArray>,
    /** Each polygon is its rings, the outside first. */
    val polygons: List<List<DoubleArray>>
)

internal fun FeatureCollection.toShapes() = Shapes(features.map { it.shaped() })

private fun Feature.shaped(): ShapedFeature {
    val points = ArrayList<LatLon>()
    val lines = ArrayList<DoubleArray>()
    val polygons = ArrayList<List<DoubleArray>>()

    fun line(positions: List<LatLon>) {
        if (positions.size >= 2) lines += positions.toWorld()
    }

    fun polygon(rings: List<List<LatLon>>) {
        polygons += rings.filter { it.size >= 2 }.map { it.toWorld() }
    }

    fun add(geometry: Geometry) {
        when (geometry) {
            is Geometry.Point -> points += geometry.position
            is Geometry.MultiPoint -> points += geometry.positions
            is Geometry.LineString -> line(geometry.positions)
            is Geometry.MultiLineString -> geometry.lines.forEach(::line)
            is Geometry.Polygon -> polygon(geometry.rings)
            is Geometry.MultiPolygon -> geometry.polygons.forEach(::polygon)
            is Geometry.GeometryCollection -> geometry.geometries.forEach(::add)
        }
    }
    geometry?.let(::add)
    return ShapedFeature(this, points.toWorld(), lines, polygons)
}

/** The feature nearest a touch, and how far, in world units. */
internal class Hit(val feature: Feature, val distance: Double)

/**
 * The feature nearest the touch at ([x], [y]) in unit Mercator, if there is one: 0 away for a
 * touch inside a polygon (and not in one of its holes), otherwise the distance to the nearest point
 * of its outline, a line or a point.
 *
 * It works in the world's coordinates and not the screen's, so it needs no projection of the
 * shapes: the map is the same shape on screen as in the world, only scaled and turned, and neither
 * changes who is nearest. A distance in pixels is this one divided by the pixels in a world unit.
 *
 * Where features are equally near the last one wins, which is the one drawn on top. Like drawing,
 * it takes every step along a shape the short way round, so a shape across the antimeridian is one
 * shape; unlike drawing, it takes the copy of the world the touch is nearest to rather than the
 * copy nearest the camera. Those only differ where the map shows the world more than once.
 */
internal fun Shapes.nearest(x: Double, y: Double): Hit? {
    var nearest: ShapedFeature? = null
    var nearestAt = Double.POSITIVE_INFINITY
    for (i in features.lastIndex downTo 0) {
        val at = features[i].distanceTo(x, y)
        if (at < nearestAt) {
            nearest = features[i]
            nearestAt = at
            if (at == 0.0) break // nothing is nearer, and what is above it has been looked at
        }
    }
    return nearest?.let { Hit(it.feature, nearestAt) }
}

internal fun ShapedFeature.distanceTo(x: Double, y: Double): Double {
    var nearest = Double.POSITIVE_INFINITY
    for (i in points.indices step 2) {
        val dx = points[i] - x
        val dy = points[i + 1] - y
        val wrapped = dx - round(dx)
        nearest = minOf(nearest, sqrt(wrapped * wrapped + dy * dy))
    }
    for (line in lines) {
        walk(line, x, y, closed = false) { ax, ay, bx, by -> nearest = minOf(nearest, originToSegment(ax, ay, bx, by)) }
    }
    for (rings in polygons) nearest = minOf(nearest, polygonDistance(rings, x, y))
    return nearest
}

/**
 * [rings] as one polygon: 0 if ([x], [y]) is inside it, otherwise the distance to its nearest edge.
 *
 * Inside is the even-odd rule over all the rings together — the same rule the polygon is filled
 * with — so a touch in a hole is outside, and is that far from the hole's edge.
 */
internal fun polygonDistance(rings: List<DoubleArray>, x: Double, y: Double): Double {
    var inside = false
    var nearest = Double.POSITIVE_INFINITY
    for (ring in rings) {
        walk(ring, x, y, closed = true) { ax, ay, bx, by ->
            // Does the edge cross the ray from the touch towards +x? Walking is relative to the
            // touch, so the touch is the origin.
            if ((ay > 0) != (by > 0) && ax + (0 - ay) * (bx - ax) / (by - ay) > 0) inside = !inside
            nearest = minOf(nearest, originToSegment(ax, ay, bx, by))
        }
    }
    return if (inside) 0.0 else nearest
}

/**
 * Calls [visit] for each edge of [world] with its ends relative to ([x], [y]), the way
 * [com.coderwise.libs.mapview.MapOverlayScope.trace] steps along it: each vertex the short way round
 * from the one before, the first on the copy of the world nearest the touch. [closed] adds the edge
 * from the last vertex back to the first.
 */
private inline fun walk(
    world: DoubleArray,
    x: Double,
    y: Double,
    closed: Boolean,
    visit: (ax: Double, ay: Double, bx: Double, by: Double) -> Unit
) {
    if (world.size < 4) return
    val startX = (world[0] - x).let { it - round(it) }
    val startY = world[1] - y
    var fromX = startX
    var fromY = startY
    for (i in 2 until world.size step 2) {
        val step = world[i] - world[i - 2]
        val toX = fromX + step - round(step)
        val toY = world[i + 1] - y
        visit(fromX, fromY, toX, toY)
        fromX = toX
        fromY = toY
    }
    if (closed) visit(fromX, fromY, startX, startY)
}

/** How far the origin is from the segment (ax, ay)-(bx, by). */
internal fun originToSegment(ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val length = dx * dx + dy * dy
    if (length == 0.0) return sqrt(ax * ax + ay * ay)
    // Where the foot of the perpendicular from the origin falls along the segment, kept on it.
    val along = (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
    val px = ax + dx * along
    val py = ay + dy * along
    return sqrt(px * px + py * py)
}
