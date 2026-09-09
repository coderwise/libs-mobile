package com.coderwise.libs.mapview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/** How far the camera can be zoomed, for anyone offering a control over it. */
val ZOOM_LIMITS = 1f..20f

/** Where the map is looking: a world position, a fractional zoom, and which way is up. */
@Stable
class MapCameraState(
    center: LatLon = LatLon(0.0, 0.0),
    zoom: Float = 3f,
    bearing: Float = 0f
) {
    internal var x by mutableDoubleStateOf(Mercator.x(center.lon))
    internal var y by mutableDoubleStateOf(Mercator.y(center.lat))

    var zoom by mutableFloatStateOf(zoom.coerceIn(ZOOM_LIMITS))
        private set

    /**
     * What is at the top of the screen, in degrees clockwise from north: 0 is north-up, 90 puts
     * east up. The map turns the other way to put it there.
     */
    var bearing by mutableFloatStateOf(bearing.mod(360f))
        private set

    val center: LatLon get() = LatLon(Mercator.lat(y), Mercator.lon(x))

    fun moveTo(center: LatLon, zoom: Float = this.zoom) {
        x = Mercator.x(center.lon)
        y = Mercator.y(center.lat)
        this.zoom = zoom.coerceIn(ZOOM_LIMITS)
    }

    /** Turns the map to put [bearing] at the top; 0 is back to north-up. */
    fun rotateTo(bearing: Float) {
        this.bearing = bearing.mod(360f)
    }

    /** A screen offset in world axes, which is where the bearing comes in. */
    internal fun toWorld(dx: Float, dy: Float): Offset {
        if (bearing == 0f) return Offset(dx, dy)
        val radians = bearing * PI / 180.0
        val c = cos(radians)
        val s = sin(radians)
        return Offset((dx * c - dy * s).toFloat(), (dx * s + dy * c).toFloat())
    }

    /** And back: a world offset as it lies on screen. */
    internal fun toScreen(dx: Double, dy: Double): Offset {
        if (bearing == 0f) return Offset(dx.toFloat(), dy.toFloat())
        val radians = bearing * PI / 180.0
        val c = cos(radians)
        val s = sin(radians)
        return Offset((dx * c + dy * s).toFloat(), (-dx * s + dy * c).toFloat())
    }

    /** Drag: pixel deltas, so the map follows the finger whichever way it is turned. */
    internal fun pan(dx: Float, dy: Float, density: Float) {
        val scale = Mercator.worldPixels(zoom, density)
        val world = toWorld(dx, dy)
        x = (x - world.x / scale).mod(1.0)
        y = (y - world.y / scale).coerceIn(0.0, 1.0)
    }

    /** Turn by [degrees], keeping the world point under [focusX]/[focusY] pinned to it. */
    internal fun rotateBy(degrees: Float, focusX: Float, focusY: Float, width: Float, height: Float, density: Float) {
        if (degrees == 0f) return
        val scale = Mercator.worldPixels(zoom, density)
        val before = toWorld(focusX - width / 2, focusY - height / 2)
        bearing = (bearing + degrees).mod(360f)
        val after = toWorld(focusX - width / 2, focusY - height / 2)
        x = (x + (before.x - after.x) / scale).mod(1.0)
        y = (y + (before.y - after.y) / scale).coerceIn(0.0, 1.0)
    }

    /** Zoom to [target], keeping the world point under [focusX]/[focusY] pinned to it. */
    internal fun zoomTo(target: Float, focusX: Float, focusY: Float, width: Float, height: Float, density: Float) {
        val clamped = target.coerceIn(ZOOM_LIMITS)
        if (clamped == zoom) return
        val focus = toWorld(focusX - width / 2, focusY - height / 2)
        val before = Mercator.worldPixels(zoom, density)
        val anchorX = x + focus.x / before
        val anchorY = y + focus.y / before
        zoom = clamped
        val after = Mercator.worldPixels(clamped, density)
        x = (anchorX - focus.x / after).mod(1.0)
        y = (anchorY - focus.y / after).coerceIn(0.0, 1.0)
    }

    /** The coordinate under a point on a map [width] x [height] pixels looking through this camera. */
    internal fun pointAt(x: Float, y: Float, width: Float, height: Float, density: Float): LatLon {
        val world = Mercator.worldPixels(zoom, density)
        val there = toWorld(x - width / 2, y - height / 2)
        val lon = Mercator.lon((this.x + there.x / world).mod(1.0))
        return LatLon(Mercator.lat(this.y + there.y / world), lon)
    }

    internal fun zoomBy(factor: Float, focusX: Float, focusY: Float, width: Float, height: Float, density: Float) =
        zoomTo(zoom + (ln(factor.toDouble()) / ln(2.0)).toFloat(), focusX, focusY, width, height, density)
}

/** A camera that survives configuration changes and process death. */
@Composable
fun rememberMapCameraState(
    center: LatLon = LatLon(0.0, 0.0),
    zoom: Float = 3f,
    bearing: Float = 0f
): MapCameraState = rememberSaveable(saver = CameraSaver) { MapCameraState(center, zoom, bearing) }

private val CameraSaver = Saver<MapCameraState, List<Any>>(
    save = { listOf(it.x, it.y, it.zoom, it.bearing) },
    restore = {
        MapCameraState(zoom = it[2] as Float, bearing = it[3] as Float)
            .apply { x = it[0] as Double; y = it[1] as Double }
    }
)
