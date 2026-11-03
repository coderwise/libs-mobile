package com.coderwise.libs.map

import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

private const val FLING_VELOCITY_THRESHOLD = 50f
private const val DOUBLE_TAP_DRAG_ZOOM_SENSITIVITY = 120.0

fun Modifier.tiledMapTapControls(
    state: TiledMapState,
    coroutineScope: CoroutineScope,
    containerWidth: Int,
    containerHeight: Int
): Modifier = pointerInput(containerWidth, containerHeight, state.tileSizePx) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)

        withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            waitForUpOrCancellation()
        } ?: return@awaitEachGesture

        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = false)
        } ?: return@awaitEachGesture

        secondDown.consume()
        state.stopAnimations()

        val pivot = Offset(
            x = secondDown.position.x - containerWidth / 2f,
            y = secondDown.position.y - containerHeight / 2f
        )
        var isDragging = false
        val startZoom = state.zoom
        var lastZoom = startZoom

        val dragCompleted = drag(secondDown.id) { change ->
            // positive totalY = dragging down = zoom in
            val totalY = change.position.y - secondDown.position.y
            if (!isDragging && abs(totalY) > viewConfiguration.touchSlop) {
                isDragging = true
            }
            if (isDragging) {
                val newZoom = startZoom + totalY.toDouble() / DOUBLE_TAP_DRAG_ZOOM_SENSITIVITY
                val incrementalFactor = 2.0.pow(newZoom - lastZoom).toFloat()
                state.applyPinchZoom(incrementalFactor, pivot)
                lastZoom = newZoom
            }
            change.consume()
        }

        if (dragCompleted && !isDragging) {
            coroutineScope.launch {
                state.animateZoomTo(state.zoom + 1.0, pivot = pivot)
            }
        }
    }
}

private const val USER_INTERACTING_RESET_MS = 2000L
private const val ROTATION_LOCK_THRESHOLD_DEGREES = 20f

fun Modifier.tiledMapTransformControls(
    state: TiledMapState,
    coroutineScope: CoroutineScope,
    containerWidth: Int,
    containerHeight: Int,
    rotateEnabled: Boolean = true
): Modifier = pointerInput(containerWidth, containerHeight, state.tileSizePx, rotateEnabled) {
    val velocityTracker = VelocityTracker()
    val decaySpec: DecayAnimationSpec<Offset> = exponentialDecay()
    val touchSlop = viewConfiguration.touchSlop
    var interactingResetJob: Job? = null

    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        state.stopAnimations()
        velocityTracker.resetTracking()

        val slop = TouchSlopAccumulator(touchSlop)
        val rotationLock = RotationLockAccumulator(ROTATION_LOCK_THRESHOLD_DEGREES)
        var wasMultiTouch = false

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) break

            if (event.changes.count { it.pressed } > 1) wasMultiTouch = true

            val pan = event.calculatePan()
            val zoom = event.calculateZoom()
            val rotation = if (rotateEnabled) event.calculateRotation() else 0f

            if (!slop.passed) {
                slop.update(pan, zoom, event.calculateCentroidSize(useCurrent = false))
            }

            if (slop.passed) {
                if (!state.isUserInteracting) {
                    interactingResetJob?.cancel()
                    state.isUserInteracting = true
                }
                state.applyPan(pan)
                val rotationDelta = rotationLock.update(rotation)
                if (rotationDelta != 0f || zoom != 1f) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val focal = Offset(
                        x = centroid.x - containerWidth / 2f,
                        y = centroid.y - containerHeight / 2f
                    )
                    if (rotationDelta != 0f) {
                        // Fingers twisting clockwise spin the content clockwise on screen,
                        // which turns the bearing counter-clockwise.
                        state.applyRotation(degrees = -rotationDelta.toDouble(), focal = focal)
                    }
                    if (zoom != 1f) {
                        state.applyPinchZoom(zoomFactor = zoom, focal = focal)
                    }
                }
                event.trackVelocity(velocityTracker)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }

            if (event.changes.none { it.pressed }) break
        }

        if (!slop.passed) return@awaitEachGesture

        // Only single-finger pans may fling. A pinch must never coast into a pan: lifting two
        // fingers off the glass is never perfectly simultaneous, and the incidental drag of the
        // last remaining finger during lift-off would otherwise register a spurious fling.
        if (!wasMultiTouch) {
            val velocity = velocityTracker.calculateVelocity()
            if (abs(velocity.x) > FLING_VELOCITY_THRESHOLD || abs(velocity.y) > FLING_VELOCITY_THRESHOLD) {
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    state.flingPan(velocity.x, velocity.y, decaySpec)
                }
            }
        }

        interactingResetJob?.cancel()
        interactingResetJob = coroutineScope.launch {
            delay(USER_INTERACTING_RESET_MS.milliseconds)
            state.isUserInteracting = false
        }
    }
}

/**
 * Adds the latest pointer position to [tracker] only while a single finger is down. Velocity is
 * only ever consumed for single-finger pans (multi-touch gestures never fling), but the tracker is
 * still cleared on multi-touch to discard any stale single-finger samples.
 */
private fun PointerEvent.trackVelocity(tracker: VelocityTracker) {
    val active = changes.filter { it.pressed }
    when {
        active.size == 1 -> tracker.addPosition(active[0].uptimeMillis, active[0].position)
        active.size > 1 -> tracker.resetTracking()
    }
}

private const val SCROLL_ZOOM_SENSITIVITY = 40.0

fun Modifier.tiledMapScrollZoomControls(
    state: TiledMapState,
    containerWidth: Int,
    containerHeight: Int
): Modifier = pointerInput(containerWidth, containerHeight) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) {
                val change = event.changes.firstOrNull() ?: continue
                val scrollY = change.scrollDelta.y
                if (scrollY == 0f) continue
                val zoomFactor = 2.0.pow(-scrollY / SCROLL_ZOOM_SENSITIVITY).toFloat()
                val focal = Offset(
                    x = change.position.x - containerWidth / 2f,
                    y = change.position.y - containerHeight / 2f
                )
                state.stopAnimations()
                state.applyPinchZoom(zoomFactor, focal)
                change.consume()
            }
        }
    }
}

/**
 * Suppresses two-finger rotation until the accumulated twist exceeds [thresholdDegrees], so an
 * ordinary pinch-zoom doesn't nudge the bearing. Rotation accumulated while locked is discarded
 * rather than replayed, avoiding a visible jump at the moment of unlock.
 */
internal class RotationLockAccumulator(private val thresholdDegrees: Float) {
    private var accumulated = 0f
    var unlocked = false
        private set

    /** Returns the rotation delta (degrees) to apply for this event: 0 while still locked. */
    fun update(delta: Float): Float {
        if (unlocked) return delta
        accumulated += delta
        if (abs(accumulated) > thresholdDegrees) unlocked = true
        return 0f
    }
}

/**
 * Accumulates pan and zoom motion across pointer events until either exceeds the touch slop,
 * matching the threshold semantics used by Compose's transformable gesture detector.
 */
internal class TouchSlopAccumulator(private val touchSlop: Float) {
    private var pan = Offset.Zero
    private var zoom = 1f
    var passed: Boolean = false
        private set

    fun update(deltaPan: Offset, zoomFactor: Float, centroidSize: Float) {
        if (passed) return
        pan += deltaPan
        zoom *= zoomFactor
        val zoomMotion = abs(1f - zoom) * centroidSize
        val panMotion = pan.getDistance()
        if (panMotion > touchSlop || zoomMotion > touchSlop) {
            passed = true
        }
    }
}
