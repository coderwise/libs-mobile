package com.coderwise.libs.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.coderwise.libs.mapcore.TileId
import com.coderwise.libs.mapcore.MapMath
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun rememberTiledMapState(
    initialLatitude: Double = 0.0,
    initialLongitude: Double = 0.0,
    initialZoom: Double = 1.0,
    initialBearing: Double = 0.0,
    tileSize: Int = 256
): TiledMapState {
    val density = LocalDensity.current
    val tileSizePx = with(density) { tileSize.dp.roundToPx() }

    return rememberSaveable(saver = TiledMapState.Saver) {
        TiledMapState(
            initialLatitude = initialLatitude,
            initialLongitude = initialLongitude,
            initialZoom = initialZoom,
            initialTileSizePx = tileSizePx,
            initialBearing = initialBearing
        )
    }.apply {
        this.tileSizePx = tileSizePx
    }
}

@Stable
class TiledMapState(
    initialLatitude: Double,
    initialLongitude: Double,
    initialZoom: Double,
    initialTileSizePx: Int = 256,
    initialBearing: Double = 0.0,
) {
    var latitude by mutableStateOf(initialLatitude.coerceIn(MIN_LATITUDE, MAX_LATITUDE))
    var longitude by mutableStateOf(wrapLongitude(initialLongitude))
    var zoom by mutableStateOf(initialZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
    var isUserInteracting by mutableStateOf(false)

    private var _bearing by mutableStateOf(wrapBearing(initialBearing))

    /**
     * Camera rotation in degrees, clockwise from north: the compass direction that points up
     * on screen. Always normalized to [0, 360).
     */
    var bearing: Double
        get() = _bearing
        set(value) {
            _bearing = wrapBearing(value)
        }

    private var _tileSizePx by mutableStateOf(initialTileSizePx)
    var tileSizePx: Int
        get() = _tileSizePx
        internal set(value) {
            _tileSizePx = value
        }

    private val zoomAnimatable = Animatable(zoom.toFloat())
    private var animationJob: Job? = null

    // Backed by derivedStateOf so readers — in composition or in a layout/measure block — only
    // invalidate when the integer zoom (resp. the scale) actually changes, rather than on every
    // fractional-zoom step. This is why panning/zooming re-lays-out the tile grid instead of
    // recomposing it; consumers get that for free just by reading these properties.
    private val _zoomInt = derivedStateOf { zoom.toInt() }
    val zoomInt: Int get() = _zoomInt.value

    private val _zoomScale = derivedStateOf { 2.0.pow(zoom - zoomInt) }
    val zoomScale: Double get() = _zoomScale.value

    val scaledTileSizePx: Int
        get() = (tileSizePx * zoomScale).toInt()

    /**
     * Rotates a screen-space pixel delta (relative to the viewport center) into world space,
     * i.e. axes aligned with the unrotated tile grid. Inverse of [worldToScreen].
     */
    internal fun screenToWorld(offset: Offset): Offset {
        if (_bearing == 0.0) return offset
        val radians = _bearing * PI / 180.0
        val cos = cos(radians)
        val sin = sin(radians)
        return Offset(
            x = (offset.x * cos - offset.y * sin).toFloat(),
            y = (offset.x * sin + offset.y * cos).toFloat()
        )
    }

    /** Rotates a world-space pixel delta into screen space. Inverse of [screenToWorld]. */
    internal fun worldToScreen(offset: Offset): Offset {
        if (_bearing == 0.0) return offset
        val radians = _bearing * PI / 180.0
        val cos = cos(radians)
        val sin = sin(radians)
        return Offset(
            x = (offset.x * cos + offset.y * sin).toFloat(),
            y = (-offset.x * sin + offset.y * cos).toFloat()
        )
    }

    /**
     * The axis-aligned bounding box, in world axes, that the viewport covers once rotated by
     * [bearing] — the area of the unrotated tile grid that can appear on screen.
     */
    internal fun rotatedViewportSize(width: Double, height: Double): Pair<Double, Double> {
        if (_bearing == 0.0) return width to height
        val radians = _bearing * PI / 180.0
        val cos = abs(cos(radians))
        val sin = abs(sin(radians))
        return (width * cos + height * sin) to (width * sin + height * cos)
    }

    fun stopAnimations() {
        animationJob?.cancel()
        animationJob = null
    }

    internal fun setAnimationJob(job: Job) {
        animationJob?.cancel()
        animationJob = job
    }

    internal fun updateLocation(latitude: Double, longitude: Double, zoom: Double = this.zoom) {
        val clampedLat = latitude.coerceIn(MIN_LATITUDE, MAX_LATITUDE)
        val clampedZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val wrappedLon = wrapLongitude(longitude)
        if (this.latitude != clampedLat || this.longitude != wrappedLon || this.zoom != clampedZoom) {
            this.latitude = clampedLat
            this.longitude = wrappedLon
            this.zoom = clampedZoom
        }
    }

    fun setLocation(latitude: Double, longitude: Double, zoom: Double = this.zoom) {
        stopAnimations()
        updateLocation(latitude, longitude, zoom)
    }

    suspend fun animateLocationTo(
        latitude: Double,
        longitude: Double,
        zoom: Double = this.zoom,
        animationSpec: AnimationSpec<Float> = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        )
    ) {
        val startLat = this.latitude
        val startLon = this.longitude
        val startZoom = this.zoom

        // Shortest path for longitude wrapping
        val destLon = startLon + ((longitude - startLon + 180.0).mod(360.0) - 180.0)

        coroutineScope {
            setAnimationJob(coroutineContext[Job.Key]!!)
            animate(0f, 1f, animationSpec = animationSpec) { value, _ ->
                updateLocation(
                    latitude = startLat + (latitude - startLat) * value.toDouble(),
                    longitude = startLon + (destLon - startLon) * value.toDouble(),
                    zoom = startZoom + (zoom - startZoom) * value.toDouble()
                )
            }
        }
    }

    suspend fun animateBearingTo(
        target: Double,
        animationSpec: AnimationSpec<Float> = tween(
            durationMillis = 350,
            easing = FastOutSlowInEasing
        )
    ) {
        val start = bearing
        // Shortest way around the circle
        val delta = ((wrapBearing(target) - start + 180.0).mod(360.0)) - 180.0
        if (delta == 0.0) return

        coroutineScope {
            setAnimationJob(coroutineContext[Job.Key]!!)
            animate(0f, 1f, animationSpec = animationSpec) { value, _ ->
                bearing = start + delta * value.toDouble()
            }
        }
    }

    suspend fun animateZoomTo(
        target: Double,
        pivot: Offset? = null,
        animationSpec: AnimationSpec<Float> = tween(
            durationMillis = 350,
            easing = FastOutSlowInEasing
        )
    ) {
        val clamped = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (clamped == zoom && !zoomAnimatable.isRunning) return

        // Bearing is constant for the duration of the animation (gestures cancel it), so the
        // screen→world conversion of the pivot can be captured once up front.
        val worldPivot = pivot?.let { screenToWorld(it) }
        val focal = worldPivot?.let {
            val startZoomInt = zoom.toInt()
            val startZoomScale = 2.0.pow(zoom - startZoomInt)
            val (centerTileX, centerTileY) = MapMath.latLonToTileFractional(
                latitude, longitude, startZoomInt.toDouble()
            )
            val focalTileX = centerTileX + it.x / (tileSizePx * startZoomScale)
            val focalTileY = centerTileY + it.y / (tileSizePx * startZoomScale)
            MapMath.tileToLatLon(focalTileX, focalTileY, startZoomInt.toDouble())
        }

        coroutineScope {
            setAnimationJob(coroutineContext[Job.Key]!!)
            zoomAnimatable.snapTo(zoom.toFloat())
            zoomAnimatable.animateTo(clamped.toFloat(), animationSpec) {
                val newZoom = value.toDouble()
                if (worldPivot != null && focal != null) {
                    val (focalLat, focalLon) = focal
                    val animZoomInt = newZoom.toInt()
                    val animZoomScale = 2.0.pow(newZoom - animZoomInt)
                    val (newFocalTileX, newFocalTileY) = MapMath.latLonToTileFractional(
                        focalLat, focalLon, animZoomInt.toDouble()
                    )
                    val newCenterTileX = newFocalTileX - worldPivot.x / (tileSizePx * animZoomScale)
                    val newCenterTileY = newFocalTileY - worldPivot.y / (tileSizePx * animZoomScale)
                    val (newLat, newLon) = MapMath.tileToLatLon(
                        newCenterTileX, newCenterTileY, animZoomInt.toDouble()
                    )
                    updateLocation(newLat, newLon, newZoom)
                } else {
                    updateLocation(latitude, longitude, newZoom)
                }
            }
        }
    }

    /**
     * Translates the center by [pan] screen-space pixels (positive x/y move the map content
     * down/right, i.e. the center moves up/left in tile coordinates).
     */
    internal fun applyPan(pan: Offset) {
        if (pan == Offset.Zero) return
        val worldPan = screenToWorld(pan)
        val zoomInt = zoom.toInt()
        val zoomScale = 2.0.pow(zoom - zoomInt)
        val (tileX, tileY) = MapMath.latLonToTileFractional(
            latitude, longitude, zoomInt.toDouble()
        )
        val pannedTileX = tileX - worldPan.x.toDouble() / (tileSizePx * zoomScale)
        val pannedTileY = tileY - worldPan.y.toDouble() / (tileSizePx * zoomScale)
        val (lat, lon) = MapMath.tileToLatLon(pannedTileX, pannedTileY, zoomInt.toDouble())
        updateLocation(lat, lon)
    }

    /**
     * Rotates the camera by [degrees] (positive turns [bearing] clockwise) around [focal], a
     * screen offset relative to the viewport center: the geographic point under [focal] stays
     * under the same screen position.
     */
    internal fun applyRotation(degrees: Double, focal: Offset = Offset.Zero) {
        if (degrees == 0.0) return
        if (focal == Offset.Zero) {
            bearing += degrees
            return
        }
        val zoomInt = zoom.toInt()
        val zoomScale = 2.0.pow(zoom - zoomInt)
        val pixelsPerTile = tileSizePx * zoomScale
        val (centerTileX, centerTileY) = MapMath.latLonToTileFractional(
            latitude, longitude, zoomInt.toDouble()
        )
        val before = screenToWorld(focal)
        val focalTileX = centerTileX + before.x / pixelsPerTile
        val focalTileY = centerTileY + before.y / pixelsPerTile
        bearing += degrees
        val after = screenToWorld(focal)
        val (newLat, newLon) = MapMath.tileToLatLon(
            focalTileX - after.x / pixelsPerTile,
            focalTileY - after.y / pixelsPerTile,
            zoomInt.toDouble()
        )
        updateLocation(newLat, newLon)
    }

    /**
     * Applies a pinch-zoom: scales [zoom] by [zoomFactor] and shifts the center so that the
     * geographic point under [focal] (offset relative to the viewport center, in pixels) stays
     * under the same screen position.
     */
    internal fun applyPinchZoom(zoomFactor: Float, focal: Offset) {
        if (zoomFactor == 1f) return
        val currentZoom = zoom
        val currentZoomInt = currentZoom.toInt()
        val currentZoomScale = 2.0.pow(currentZoom - currentZoomInt)
        val newZoom = (currentZoom + ln(zoomFactor.toDouble()) / ln(2.0))
            .coerceIn(MIN_ZOOM, MAX_ZOOM)
        val newZoomInt = newZoom.toInt()
        val newZoomScale = 2.0.pow(newZoom - newZoomInt)

        val worldFocal = screenToWorld(focal)
        val (centerTileX, centerTileY) = MapMath.latLonToTileFractional(
            latitude, longitude, currentZoomInt.toDouble()
        )
        val focalTileX = centerTileX + worldFocal.x / (tileSizePx * currentZoomScale)
        val focalTileY = centerTileY + worldFocal.y / (tileSizePx * currentZoomScale)
        val (focalLat, focalLon) = MapMath.tileToLatLon(
            focalTileX, focalTileY, currentZoomInt.toDouble()
        )

        val (newFocalTileX, newFocalTileY) = MapMath.latLonToTileFractional(
            focalLat, focalLon, newZoomInt.toDouble()
        )
        val newCenterTileX = newFocalTileX - worldFocal.x / (tileSizePx * newZoomScale)
        val newCenterTileY = newFocalTileY - worldFocal.y / (tileSizePx * newZoomScale)
        val (newLat, newLon) = MapMath.tileToLatLon(
            newCenterTileX, newCenterTileY, newZoomInt.toDouble()
        )
        updateLocation(newLat, newLon, newZoom)
    }

    /**
     * Decays a fling velocity (pixels/second) into a panning animation at the current zoom.
     */
    internal suspend fun flingPan(
        velocityX: Float,
        velocityY: Float,
        decaySpec: DecayAnimationSpec<Offset>
    ) {
        val flingZoom = zoom
        val flingZoomInt = flingZoom.toInt()
        val flingZoomScale = 2.0.pow(flingZoom - flingZoomInt)
        val (startTileX, startTileY) = MapMath.latLonToTileFractional(
            latitude, longitude, flingZoomInt.toDouble()
        )

        coroutineScope {
            setAnimationJob(coroutineContext[Job.Key]!!)
            AnimationState(
                typeConverter = Offset.VectorConverter,
                initialValue = Offset.Zero,
                initialVelocityVector = AnimationVector2D(velocityX, velocityY),
            ).animateDecay(decaySpec) {
                // Bearing cannot change mid-fling (touch cancels the animation), so the
                // screen→world rotation is effectively constant here.
                val worldValue = screenToWorld(value)
                val currentTileX = startTileX - worldValue.x.toDouble() / (tileSizePx * flingZoomScale)
                val currentTileY = startTileY - worldValue.y.toDouble() / (tileSizePx * flingZoomScale)
                val (newLat, newLon) = MapMath.tileToLatLon(
                    currentTileX, currentTileY, flingZoomInt.toDouble()
                )
                updateLocation(newLat, newLon)
            }
        }
    }

    fun isLatLonOnScreen(
        latitude: Double,
        longitude: Double,
        viewportWidth: Int,
        viewportHeight: Int
    ): Boolean {
        val zoomInt = zoom.toInt()
        val zoomScale = 2.0.pow(zoom - zoomInt)
        val tileCount = 2.0.pow(zoomInt)
        val (tx, ty) = MapMath.latLonToTileFractional(latitude, longitude, zoomInt.toDouble())
        val (cx, cy) = MapMath.latLonToTileFractional(this.latitude, this.longitude, zoomInt.toDouble())
        val dx = ((tx - cx + tileCount / 2.0).mod(tileCount)) - tileCount / 2.0
        val dy = ty - cy
        val pixelsPerTile = tileSizePx * zoomScale
        val screen = worldToScreen(
            Offset((dx * pixelsPerTile).toFloat(), (dy * pixelsPerTile).toFloat())
        )
        return abs(screen.x) <= viewportWidth / 2.0 && abs(screen.y) <= viewportHeight / 2.0
    }

    /**
     * The tiles that become visible the instant [zoom] crosses into an adjacent integer level
     * at the current center: crossing up into zoomInt+1 resets zoomScale to ~1 (one tile spans
     * tileSizePx), crossing down into zoomInt-1 lands at zoomScale ~2 (one tile spans
     * 2*tileSizePx). Pure geometry — callers decide whether and how to prefetch them.
     */
    fun adjacentZoomTiles(viewportWidth: Int, viewportHeight: Int): List<TileId> {
        if (viewportWidth <= 0 || viewportHeight <= 0) return emptyList()

        val zoomInt = zoomInt
        val (cfx, cfy) = MapMath.latLonToTileFractional(latitude, longitude, zoomInt.toDouble())
        // A rotated viewport sweeps a larger axis-aligned area of the tile grid
        val (effectiveWidth, effectiveHeight) =
            rotatedViewportSize(viewportWidth.toDouble(), viewportHeight.toDouble())
        // LinkedHashSet dedupes X wrap-around collisions at low zoom levels
        val tiles = LinkedHashSet<TileId>()

        fun addViewportTiles(zoom: Int, cx: Double, cy: Double, pixelsPerTile: Double) {
            val tileCount = 1 shl zoom
            val halfWidthTiles = (effectiveWidth / 2.0) / pixelsPerTile
            val halfHeightTiles = (effectiveHeight / 2.0) / pixelsPerTile
            for (tx in floor(cx - halfWidthTiles).toInt()..floor(cx + halfWidthTiles).toInt()) {
                for (ty in floor(cy - halfHeightTiles).toInt()..floor(cy + halfHeightTiles).toInt()) {
                    if (ty in 0 until tileCount) {
                        tiles.add(TileId(zoom, tx.mod(tileCount), ty))
                    }
                }
            }
        }

        if (zoomInt + 1 <= MAX_ZOOM.toInt()) {
            addViewportTiles(zoomInt + 1, cfx * 2, cfy * 2, tileSizePx.toDouble())
        }
        if (zoomInt - 1 >= MIN_ZOOM.toInt()) {
            addViewportTiles(zoomInt - 1, cfx / 2, cfy / 2, tileSizePx * 2.0)
        }
        return tiles.toList()
    }

    suspend fun zoomIn() {
        val base = if (zoomAnimatable.isRunning) zoomAnimatable.targetValue.toDouble() else zoom
        animateZoomTo(base + 1.0)
    }

    suspend fun zoomOut() {
        val base = if (zoomAnimatable.isRunning) zoomAnimatable.targetValue.toDouble() else zoom
        animateZoomTo(base - 1.0)
    }

    companion object {
        const val MIN_ZOOM = 0.0
        const val MAX_ZOOM = 19.0
        const val MIN_LATITUDE = -85.05112878
        const val MAX_LATITUDE = 85.05112878

        private fun wrapLongitude(longitude: Double): Double =
            ((longitude + 180.0).mod(360.0) - 180.0)

        private fun wrapBearing(bearing: Double): Double = bearing.mod(360.0)

        val Saver: Saver<TiledMapState, *> = listSaver(
            save = { listOf(it.latitude, it.longitude, it.zoom, it.tileSizePx.toDouble(), it.bearing) }
        ) {
            TiledMapState(
                initialLatitude = it[0],
                initialLongitude = it[1],
                initialZoom = it[2],
                initialTileSizePx = it[3].toInt(),
                initialBearing = it.getOrElse(4) { 0.0 }
            )
        }
    }
}