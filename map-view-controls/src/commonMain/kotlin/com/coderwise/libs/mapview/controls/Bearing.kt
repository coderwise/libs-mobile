package com.coderwise.libs.mapview.controls

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.coderwise.libs.mapview.MapCameraState
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/** The angle to turn from [from] to [to] by the shorter way: in `[-180, 180)`, positive clockwise. */
internal fun shortestTurn(from: Float, to: Float): Float = (to - from + 540f).mod(360f) - 180f

/**
 * Turns the map to put [bearing] at the top, animated and by the shorter way round: from 350 to 10
 * is twenty degrees, not three hundred and forty. Cancel the coroutine to stop it.
 */
suspend fun MapCameraState.turnTo(bearing: Float, animationSpec: AnimationSpec<Float> = spring()) {
    val from = this.bearing
    val by = shortestTurn(from, bearing)
    if (by != 0f) {
        animate(0f, 1f, animationSpec = animationSpec) { fraction, _ -> rotateTo(from + by * fraction) }
    }
    // Where it was asked to end up exactly, not where the arithmetic of getting there left it: a
    // bearing that finishes a hair off north is a map the north button still thinks is turned.
    rotateTo(bearing)
}

/**
 * Course-up: keeps the top of the map pointing the way [heading] does — a heading in degrees
 * clockwise from north, null while there is none, such as standing still.
 *
 * Nothing is drawn. It turns the camera, the short way round and animated, whenever [heading] or
 * [enabled] changes, and it waits while the user has hold of the map rather than fighting them for
 * it. A heading within [deadband] degrees of the way the map already faces is left alone, because a
 * GPS heading wobbles by about that much and a map that twitches with it is unreadable.
 *
 * Pair it with `Modifier.mapGestures(camera, rotatable = !courseUp)`: a map that follows the
 * heading has no use for a twist that fights it.
 */
@Composable
fun CourseUp(
    camera: MapCameraState,
    heading: Float?,
    enabled: Boolean = true,
    deadband: Float = 2f
) {
    LaunchedEffect(camera, heading, enabled) {
        if (!enabled || heading == null) return@LaunchedEffect
        snapshotFlow { camera.isInteracting }.first { !it }
        if (abs(shortestTurn(camera.bearing, heading)) >= deadband) camera.turnTo(heading)
    }
}
