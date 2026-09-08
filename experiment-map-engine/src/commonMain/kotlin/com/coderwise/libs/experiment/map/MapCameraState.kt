package com.coderwise.libs.experiment.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.ln

/** How far the camera can be zoomed, for anyone offering a control over it. */
val ZOOM_LIMITS = 1f..20f

/** Where the map is looking: a world position and a fractional zoom. */
@Stable
class MapCameraState(center: LatLon = LatLon(0.0, 0.0), zoom: Float = 3f) {
    internal var x by mutableDoubleStateOf(Mercator.x(center.lon))
    internal var y by mutableDoubleStateOf(Mercator.y(center.lat))

    var zoom by mutableFloatStateOf(zoom.coerceIn(ZOOM_LIMITS))
        private set

    val center: LatLon get() = LatLon(Mercator.lat(y), Mercator.lon(x))

    fun moveTo(center: LatLon, zoom: Float = this.zoom) {
        x = Mercator.x(center.lon)
        y = Mercator.y(center.lat)
        this.zoom = zoom.coerceIn(ZOOM_LIMITS)
    }

    /** Drag: pixel deltas, so the map follows the finger. */
    internal fun pan(dx: Float, dy: Float, density: Float) {
        val scale = Mercator.worldPixels(zoom, density)
        x = (x - dx / scale).mod(1.0)
        y = (y - dy / scale).coerceIn(0.0, 1.0)
    }

    /** Zoom to [target], keeping the world point under [focusX]/[focusY] pinned to it. */
    internal fun zoomTo(target: Float, focusX: Float, focusY: Float, width: Float, height: Float, density: Float) {
        val clamped = target.coerceIn(ZOOM_LIMITS)
        if (clamped == zoom) return
        val before = Mercator.worldPixels(zoom, density)
        val anchorX = x + (focusX - width / 2) / before
        val anchorY = y + (focusY - height / 2) / before
        zoom = clamped
        val after = Mercator.worldPixels(clamped, density)
        x = (anchorX - (focusX - width / 2) / after).mod(1.0)
        y = (anchorY - (focusY - height / 2) / after).coerceIn(0.0, 1.0)
    }

    /** The coordinate under a point on a map [width] x [height] pixels looking through this camera. */
    internal fun pointAt(x: Float, y: Float, width: Float, height: Float, density: Float): LatLon {
        val world = Mercator.worldPixels(zoom, density)
        val lon = Mercator.lon((this.x + (x - width / 2) / world).mod(1.0))
        return LatLon(Mercator.lat(this.y + (y - height / 2) / world), lon)
    }

    internal fun zoomBy(factor: Float, focusX: Float, focusY: Float, width: Float, height: Float, density: Float) =
        zoomTo(zoom + (ln(factor.toDouble()) / ln(2.0)).toFloat(), focusX, focusY, width, height, density)
}

/** A camera that survives configuration changes and process death. */
@Composable
fun rememberMapCameraState(center: LatLon = LatLon(0.0, 0.0), zoom: Float = 3f): MapCameraState =
    rememberSaveable(saver = CameraSaver) { MapCameraState(center, zoom) }

private val CameraSaver = Saver<MapCameraState, List<Any>>(
    save = { listOf(it.x, it.y, it.zoom) },
    restore = { MapCameraState(zoom = it[2] as Float).apply { x = it[0] as Double; y = it[1] as Double } }
)
