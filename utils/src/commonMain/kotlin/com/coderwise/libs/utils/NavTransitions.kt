package com.coderwise.libs.utils

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay

private const val VERTICAL_DURATION = 400
private const val HORIZONTAL_DURATION = 700

/**
 * Marks the screen carrying a transition, so its predictive-pop spec can tell a push from a back.
 * On web NavDisplay reports a plain push as a predictive back, which would otherwise play the back
 * slide (entering from the left, or from the top) on the way in.
 */
private const val ENTERING_MARKER = "slideTransitionEntering"

/** Vertical slide both ways: the screen rises from the bottom; back sinks it again. */
fun verticalSlideTransition() = NavDisplay.transitionSpec {
    slideInVerticalPushTransform()
} + NavDisplay.popTransitionSpec {
    slideInVerticalPopTransform()
} + mapOf(ENTERING_MARKER to true) + NavDisplay.predictivePopTransitionSpec { _ ->
    if (targetState.metadata.containsKey(ENTERING_MARKER)) {
        slideInVerticalPushTransform()
    } else {
        slideInVerticalPopTransform()
    }
}

/**
 * The back slide for a screen pushed over another: the one underneath eases in from a quarter of
 * the way off the left while the screen on top slides out to the right.
 *
 * No fade on the incoming side. Fading makes the screen translucent, which sends all of it through
 * an offscreen layer on every frame of the transition; for a busy screen such as a map that
 * starved the render thread into an ANR.
 */
fun popTransition() = NavDisplay.popTransitionSpec {
    slideInPopTransform()
} + mapOf(ENTERING_MARKER to true) + NavDisplay.predictivePopTransitionSpec { _ ->
    if (targetState.metadata.containsKey(ENTERING_MARKER)) {
        slideInPushTransform()
    } else {
        slideInPopTransform()
    }
}

/**
 * Horizontal slide both ways: the screen enters from the right while the one beneath eases off to
 * the left, and back does the reverse. Same no-fade rule as [popTransition].
 */
fun horizontalSlideTransition() = NavDisplay.transitionSpec {
    slideInPushTransform()
} + NavDisplay.popTransitionSpec {
    slideInPopTransform()
} + popTransition()

/** The horizontal push: the new screen slides in from the right over the old one. */
fun slideInPushTransform(): ContentTransform =
    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(HORIZONTAL_DURATION)) togetherWith
        slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(HORIZONTAL_DURATION))

/** The horizontal pop: the screen underneath eases in from the left as the top one slides out right. */
fun slideInPopTransform(): ContentTransform =
    slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(HORIZONTAL_DURATION)) togetherWith
        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(HORIZONTAL_DURATION))

private fun slideInVerticalPushTransform(): ContentTransform =
    slideInVertically(initialOffsetY = { it }, animationSpec = tween(VERTICAL_DURATION)) togetherWith
        slideOutVertically(targetOffsetY = { -it / 4 }, animationSpec = tween(VERTICAL_DURATION))

private fun slideInVerticalPopTransform(): ContentTransform =
    slideInVertically(initialOffsetY = { -it / 4 }, animationSpec = tween(VERTICAL_DURATION)) togetherWith
        slideOutVertically(targetOffsetY = { it }, animationSpec = tween(VERTICAL_DURATION))
