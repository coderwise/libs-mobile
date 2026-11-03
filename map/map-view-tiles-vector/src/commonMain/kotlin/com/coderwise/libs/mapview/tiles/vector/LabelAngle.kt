package com.coderwise.libs.mapview.tiles.vector

import kotlin.math.PI

/**
 * Folds an angle in radians into `(-pi, pi]`, so 359 degrees off is one degree off.
 *
 * Every piece of label maths that compares two directions needs this, and needs it for the same
 * reason: a due-west line's tangents straddle the +-pi seam, and read as half a turn without it.
 */
fun wrapToPi(radians: Float): Float {
    var a = radians
    while (a <= -PI_F) a += 2f * PI_F
    while (a > PI_F) a -= 2f * PI_F
    return a
}

private const val PI_F = PI.toFloat()
