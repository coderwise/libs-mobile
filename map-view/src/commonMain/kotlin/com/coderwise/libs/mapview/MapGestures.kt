package com.coderwise.libs.mapview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Drag to pan, pinch to zoom, double-tap to zoom in, throw to fling — and double-tap-and-hold,
 * then drag, to zoom with one finger, which is the gesture a phone in one hand can actually do.
 *
 * Put it on whatever holds the map and its layers, so they all move together.
 *
 * A mouse wheel zooms about the pointer, which is the only gesture a desktop or a browser has.
 *
 * Two fingers also turn the map, unless [rotatable] says not to. A turn has a slop of its own —
 * a pinch is not a twist until it is clearly one — because a map that tilts off north whenever
 * two fingers are slightly uneven is worse than one that cannot turn at all.
 *
 * [onTap] is where a tap on the map lands: dropping a pin, clearing a selection, asking what is
 * here. It sits outside everything the map draws, so it hears only the taps nothing inside took —
 * a marker, a line — and it waits out the double-tap window first, since until that closes a tap
 * might still turn into a zoom.
 */
@Composable
fun Modifier.mapGestures(
    camera: MapCameraState,
    rotatable: Boolean = true,
    onTap: ((LatLon) -> Unit)? = null
): Modifier {
    val scope = rememberCoroutineScope()
    // Android's scroll-fling spline stops a map dead — a 2000 px/s flick coasted a third of a
    // screen. Maps want low friction: you throw the world and it drifts.
    val decay = remember { exponentialDecay<Offset>(frictionMultiplier = 0.45f, absVelocityThreshold = 60f) }
    var fling by remember { mutableStateOf<Job?>(null) }

    return pointerInput(camera, rotatable) {
        detectMapGestures(
            onStart = {
                fling?.cancel() // touching the map catches it
                camera.isInteracting = true
            },
            onGesture = { centroid, pan, zoom, turn ->
                camera.pan(pan.x, pan.y, density)
                if (zoom != 1f) camera.zoomBy(zoom, centroid.x, centroid.y, size.width.toFloat(), size.height.toFloat(), density)
                if (turn != 0f && rotatable) {
                    camera.rotateBy(-turn, centroid.x, centroid.y, size.width.toFloat(), size.height.toFloat(), density)
                }
            },
            onEnd = { velocity ->
                // Thresholds in dp per second, so a flick means the same thing on any screen.
                val throwing = flingVelocity(velocity.x, velocity.y, MIN_FLING_DP * density, MAX_FLING_DP * density)
                if (throwing == null) {
                    camera.isInteracting = false
                    return@detectMapGestures
                }
                // The throw is still the user's: the map is theirs until it comes to rest.
                fling = scope.launch {
                    try {
                        var last = Offset.Zero
                        Animatable(Offset.Zero, Offset.VectorConverter).animateDecay(throwing, decay) {
                            camera.pan(value.x - last.x, value.y - last.y, density)
                            last = value
                        }
                    } finally {
                        camera.isInteracting = false
                    }
                }
            }
        )
    }.pointerInput(camera) {
        // A wheel, where there is one. Zooming about the pointer rather than the middle is what
        // makes a wheel usable for reaching somewhere: point at it and scroll.
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val scroll = change.scrollDelta.y
                if (scroll == 0f) continue
                fling?.cancel()
                camera.zoomTo(
                    target = camera.zoom - scroll / SCROLL_PER_LEVEL,
                    focusX = change.position.x,
                    focusY = change.position.y,
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                    density = density
                )
                change.consume()
            }
        }
    }.pointerInput(camera, onTap) {
        // Innermost, so it is offered the drag before panning is: what starts as a second tap
        // and then moves is a zoom, and consuming it is what tells the pan detector to let go.
        detectDoubleTap(
            onZoom = { at, dy ->
                fling?.cancel()
                val levels = dy / size.height * ZOOM_PER_SCREEN
                camera.zoomTo(
                    camera.zoom + levels,
                    at.x, at.y, size.width.toFloat(), size.height.toFloat(), density
                )
            },
            onTap = { at ->
                onTap?.invoke(
                    camera.pointAt(at.x, at.y, size.width.toFloat(), size.height.toFloat(), density)
                )
            },
            onZoomIn = { at ->
                fling?.cancel()
                scope.launch {
                    animate(camera.zoom, floor(camera.zoom) + 1f) { zoom, _ ->
                        camera.zoomTo(zoom, at.x, at.y, size.width.toFloat(), size.height.toFloat(), density)
                    }
                }
            }
        )
    }
}

/**
 * How much a drag of the whole screen height is worth, in zoom levels. Downwards zooms in, the way
 * a pinch outwards does when the fingers part in that direction.
 */
private const val ZOOM_PER_SCREEN = 4f

/**
 * How much scrolling makes a whole zoom level. Wheels report their own units — a notch on one
 * machine is not a notch on another — so this is the figure the old engine was tuned with, kept so
 * that a wheel feels as it did.
 */
private const val SCROLL_PER_LEVEL = 40f

/** How far two fingers must turn between them before the map takes it as a turn. */
private const val TURN_SLOP_DEGREES = 7f

internal const val MIN_FLING_DP = 20f
internal const val MAX_FLING_DP = 3_000f

/** Null if the lift-off was a drag rather than a throw; otherwise clamped so nothing teleports. */
internal fun flingVelocity(vx: Float, vy: Float, min: Float = MIN_FLING_DP, max: Float = MAX_FLING_DP): Offset? {
    val speed = hypot(vx, vy)
    if (speed < min) return null
    val scale = if (speed > max) max / speed else 1f
    return Offset(vx * scale, vy * scale)
}

/**
 * How fast the finger was going when it left, measured across the tail of the drag.
 *
 * Only the samples taken while the pointer is down count. The release is not one of them: it
 * lands whenever the system gets round to it — hundreds of milliseconds late, at a position a few
 * pixels off wherever the pointer settled — and a throw measured across that reads as a slow
 * drift in an arbitrary direction, which is a fling that works one time in three.
 *
 * Compose's own VelocityTracker is not used here because it looks back only 100 ms and wants
 * several samples inside that window: a stalled frame coalesces the moves away and leaves it too
 * little to fit, so the fling reads zero exactly when the map is heaviest. Displacement over
 * elapsed time needs two samples and survives that.
 */
internal class LiftOff {
    private val times = ArrayDeque<Long>()
    private val positions = ArrayDeque<Offset>()

    fun reset() {
        times.clear()
        positions.clear()
    }

    fun add(change: PointerInputChange) {
        change.historical.fastForEach { add(it.uptimeMillis, it.position) }
        add(change.uptimeMillis, change.position)
    }

    fun add(at: Long, position: Offset) {
        // A sample a whole window after the last one is not the same motion: the pointer stopped
        // somewhere in the gap, and measuring across it would read a throw that is already over.
        if (times.isNotEmpty() && at - times.last() > WINDOW_MS) reset()

        times.addLast(at)
        positions.addLast(position)
        // Everything inside the window, plus the one sample before it, so a stalled frame that
        // swallows the moves still leaves two points to measure across.
        while (times.size > 2 && at - times[1] > WINDOW_MS) {
            times.removeFirst()
            positions.removeFirst()
        }
    }

    fun velocity(): Offset {
        if (times.size < 2) return Offset.Zero
        val seconds = (times.last() - times.first()) / 1000f
        return if (seconds > 0f) (positions.last() - positions.first()) / seconds else Offset.Zero
    }

    private companion object {
        const val WINDOW_MS = 100
    }
}

/**
 * The two things a second tap can turn into: [onZoomIn] when it is a plain double tap, and
 * [onZoom] for every step of a drag when the second tap is held instead of released. Both are
 * anchored on where the *first* tap landed, so the place being zoomed into stays put rather than
 * following the finger. When no second tap arrives, the first was a plain [onTap].
 */
private suspend fun PointerInputScope.detectDoubleTap(
    onZoom: (at: Offset, dy: Float) -> Unit,
    onTap: (at: Offset) -> Unit,
    onZoomIn: (at: Offset) -> Unit
) = awaitEachGesture {
    val first = awaitFirstDown(requireUnconsumed = false)
    // A tap that turns into a drag or a pinch is not the start of a double tap — and one already
    // taken by something on the map is not a tap on the map.
    waitForUpOrCancellation() ?: return@awaitEachGesture
    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        awaitFirstDown(requireUnconsumed = false)
    } ?: run {
        onTap(first.position)
        return@awaitEachGesture
    }

    var zooming = false
    var last = second.position
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == second.id } ?: break
        if (!change.pressed) break
        // Held and moved: a zoom. Sideways movement is ignored, so a wobbly drag still zooms
        // straight, and the slop is what keeps a shaky double tap from nudging the zoom.
        if (zooming || abs(change.position.y - second.position.y) > viewConfiguration.touchSlop) {
            zooming = true
            onZoom(first.position, change.position.y - last.y)
            last = change.position
            change.consume()
        }
    }
    if (!zooming) onZoomIn(first.position)
}

/**
 * Pan and pinch, plus the lift-off velocity a fling needs. Velocity is measured only while exactly
 * one finger is down — the centroid jumps when a pointer goes down or up, and that jump would
 * invent velocity nobody produced.
 */
private suspend fun PointerInputScope.detectMapGestures(
    onStart: () -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, turn: Float) -> Unit,
    onEnd: (Offset) -> Unit
) = awaitEachGesture {
    var zoom = 1f
    var pan = Offset.Zero
    var turn = 0f
    var turning = false
    var pastSlop = false
    var tracked: PointerId? = null
    var cancelled = false
    val liftOff = LiftOff()

    awaitFirstDown(requireUnconsumed = false)
    onStart()

    do {
        val event = awaitPointerEvent()
        cancelled = event.changes.fastAny { it.isConsumed }
        if (!cancelled) {
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            val turnChange = event.calculateRotation()
            if (!pastSlop) {
                zoom *= zoomChange
                pan += panChange
                val motion = abs(1 - zoom) * event.calculateCentroidSize(useCurrent = false)
                pastSlop = motion > viewConfiguration.touchSlop || pan.getDistance() > viewConfiguration.touchSlop
            }
            // A twist has to be meant: until the fingers have turned this far between them the
            // map stays where it is, and after that every degree counts.
            if (!turning) {
                turn += turnChange
                turning = abs(turn) > TURN_SLOP_DEGREES
            }
            if (pastSlop || turning) {
                onGesture(
                    event.calculateCentroid(useCurrent = false),
                    panChange,
                    zoomChange,
                    if (turning) turnChange else 0f
                )
                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
            }
            val down = event.changes.filter { it.pressed }
            when {
                down.size > 1 -> { liftOff.reset(); tracked = null }
                // Nothing is sampled once the pointer is up: see LiftOff.
                down.size == 1 -> down.first().let {
                    if (tracked != it.id) { liftOff.reset(); tracked = it.id }
                    liftOff.add(it)
                }
            }
        }
    } while (!cancelled && event.changes.fastAny { it.pressed })

    if (!cancelled && tracked != null) onEnd(liftOff.velocity())
}
