package com.coderwise.libs.mapview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Where a camera has to be to frame something: the [center] and the [zoom]. */
data class CameraFit(val center: LatLon, val zoom: Float)

/**
 * Where [point] falls in a map [width] x [height] pixels looking through this camera, in pixels from
 * its top left — what `project` gives an overlay, for code that has no overlay.
 *
 * [density] is the screen's, as for a map's own size: the sizes are in pixels and a tile is 256 dp.
 * The world repeats east and west, so a point is placed on the copy nearest the camera, and the
 * bearing is applied: this is where the point is drawn, not where it lies on an unturned map.
 */
fun MapCameraState.screenPosition(point: LatLon, width: Float, height: Float, density: Float): Offset {
    val world = Mercator.worldPixels(zoom, density)
    val dx = Mercator.nearest(Mercator.x(point.lon) - x)
    val dy = Mercator.y(point.lat) - y
    val on = toScreen(dx * world, dy * world)
    return Offset(on.x + width / 2, on.y + height / 2)
}

/**
 * Whether [point] is on a map [width] x [height] pixels looking through this camera, or within
 * [margin] pixels outside it. A negative [margin] asks whether it is that far *inside*: clear of
 * the edges, say, or of the controls over them.
 */
fun MapCameraState.isOnScreen(
    point: LatLon,
    width: Float,
    height: Float,
    density: Float,
    margin: Float = 0f
): Boolean {
    val on = screenPosition(point, width, height, density)
    return on.x >= -margin && on.x <= width + margin && on.y >= -margin && on.y <= height + margin
}

/**
 * The camera that frames all of [points] in a map of [size] pixels at [density], or null if there
 * are none. Hand the result to [MapCameraState.moveTo] or [flyTo].
 *
 * The points end up as large as they can be with every one at least [padding] from every edge. It
 * is the zoom that is solved for, not the centre: the centre is the middle of the points' extent as
 * the map will show it, which is not the middle of their latitudes and longitudes. Mercator
 * stretches towards the poles, so the middle of a tall span is not halfway between its latitudes.
 *
 * - **Across the antimeridian**, the short way. Points at 179° and -179° are two degrees apart,
 *   and the centre lands on the date line rather than on the Greenwich meridian with the whole
 *   world in view. The points are framed by the smallest arc of longitude that holds them all.
 * - **With a bearing**, the extent is measured as it will lie on screen. A long east-west line
 *   fits a portrait map twice as large once the map is turned to run it up the screen. Pass the
 *   bearing the map will be at, not the one it is at now.
 * - **A single point**, or points that all coincide, have no extent, so the zoom is [maxZoom]: a
 *   map cannot frame nothing any tighter than that.
 *
 * The zoom is held to [ZOOM_LIMITS] and to [maxZoom]; the latter is for a source that stops short
 * of the limit or a map that should not zoom in further than a street. Points too far apart for
 * the widest view the map has are given that view, and some of them are off screen: check with
 * [isOnScreen] if that matters.
 */
fun fitCamera(
    points: List<LatLon>,
    size: Size,
    density: Float,
    padding: Dp = 0.dp,
    bearing: Float = 0f,
    maxZoom: Float = ZOOM_LIMITS.endInclusive
): CameraFit? {
    if (points.isEmpty()) return null

    // The smallest arc of the world's circumference that holds every point: everything but the
    // largest gap between neighbours. Its middle is the reference column the points are measured
    // from, taking each the short way round it.
    val xs = points.map { Mercator.x(it.lon) }.sorted()
    var gapEnd = 0
    var gap = xs[0] + 1.0 - xs.last()
    for (i in 1 until xs.size) {
        if (xs[i] - xs[i - 1] > gap) {
            gap = xs[i] - xs[i - 1]
            gapEnd = i
        }
    }
    val refX = (xs[gapEnd] + (1.0 - gap) / 2).mod(1.0)
    val refY = Mercator.y(points[0].lat)

    // Measured on screen: rotated by the bearing the same way the camera does.
    val radians = bearing * PI / 180.0
    val c = cos(radians)
    val s = sin(radians)
    var minX = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    points.forEach {
        val dx = Mercator.nearest(Mercator.x(it.lon) - refX)
        val dy = Mercator.y(it.lat) - refY
        val sx = dx * c + dy * s
        val sy = -dx * s + dy * c
        minX = min(minX, sx); maxX = max(maxX, sx)
        minY = min(minY, sy); maxY = max(maxY, sy)
    }

    // The middle of that extent, turned back into the world.
    val midX = (minX + maxX) / 2
    val midY = (minY + maxY) / 2
    val centerX = (refX + midX * c - midY * s).mod(1.0)
    val centerY = (refY + midX * s + midY * c).coerceIn(0.0, 1.0)

    // The world is WORLD_TILE * density * 2^zoom pixels across; the extent has to fit what the
    // padding leaves of each side, so the tighter of the two decides. An extent with no width, or
    // no height, never limits that side.
    val paddingPx = padding.value * density
    fun pixelsPerWorld(available: Float, extent: Double) =
        if (extent > 0.0) max(available - 2 * paddingPx, 1f) / extent else Double.POSITIVE_INFINITY
    val pixels = min(pixelsPerWorld(size.width, maxX - minX), pixelsPerWorld(size.height, maxY - minY))
    val limit = min(maxZoom, ZOOM_LIMITS.endInclusive)
    val zoom = if (pixels.isInfinite()) {
        limit
    } else {
        (ln(pixels / (Mercator.WORLD_TILE * density)) / ln(2.0)).toFloat().coerceIn(ZOOM_LIMITS.start, limit)
    }
    return CameraFit(LatLon(Mercator.lat(centerY), Mercator.lon(centerX)), zoom)
}
