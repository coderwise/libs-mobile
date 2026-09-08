package com.coderwise.libs.mapview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.round

/**
 * Things placed by *where they are* rather than by which tile they fall in: a recorded track, a
 * route, a pin on a search result, a circle round the user.
 *
 * A tile layer draws what a source shipped; an overlay draws what the app knows. The two differ in
 * what positions them, which is all this scope is: the map's projection, and a way to hang a
 * composable off a coordinate.
 */
@LayoutScopeMarker
sealed interface MapOverlayScope {
    /** Where [point] falls in the map, in pixels from its top left. Follows the camera. */
    fun project(point: LatLon): Offset

    /** The point under [offset], the other way round — what a tap on the map is. */
    fun unproject(offset: Offset): LatLon

    /**
     * Puts the content on the map at [point]; [anchor] is the part of the content that lands
     * there — the middle of a dot, the tip of a pin ([Alignment.BottomCenter]).
     *
     * The content is measured as it likes and sized in dp, so it is the same on screen at every
     * zoom. Everything else about it — what it looks like, whether it is clickable — is the
     * caller's, as it would be anywhere else in a layout.
     */
    fun Modifier.at(point: LatLon, anchor: Alignment = Alignment.Center): Modifier
}

/**
 * A line through [points]: a trail, a route, a track as it is recorded.
 *
 * Stroked in dp, so it stays the same width at every zoom, and it is one path however far it runs
 * off screen — clipping a million-point track is the graphics layer's job, not ours.
 */
@Composable
fun MapOverlayScope.Polyline(
    points: List<LatLon>,
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 3.dp,
    touchWidth: Dp = 24.dp,
    onClick: (() -> Unit)? = null
) {
    // Projecting a point is a logarithm and a tangent plus an affine, and only the affine depends
    // on the camera. A track pays for the hard half once instead of on every frame it is drawn.
    val world = remember(points) {
        DoubleArray(points.size * 2).also {
            points.forEachIndexed { i, point ->
                it[i * 2] = Mercator.x(point.lon)
                it[i * 2 + 1] = Mercator.y(point.lat)
            }
        }
    }
    val path = remember { Path() }
    val overlay = this as MapOverlayState

    Canvas(modifier.fillMaxSize().tappable(overlay, world, touchWidth, onClick)) {
        path.rewind()
        overlay.trace(path, world) // reads the camera, so the line redraws as the map moves
        drawPath(
            path = path,
            color = color,
            style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * A tap within [touchWidth] of the line, and nothing else.
 *
 * A line covers the whole map however thin it looks, and Compose stops hit testing at the first
 * sibling it lands on — so a plain `pointerInput` here would make this the only tappable thing on
 * the map, whatever it was drawn over. Sharing the input with its siblings is what lets the tap
 * carry on down to the next line, and to the map underneath when it misses them all.
 *
 * Only the lift is consumed, and only once the gesture is known to be a tap: a drag that starts on
 * the line still pans the map, because panning cancels this before the finger comes up.
 */
private fun Modifier.tappable(
    overlay: MapOverlayState,
    world: DoubleArray,
    touchWidth: Dp,
    onClick: (() -> Unit)?
): Modifier = if (onClick == null) this else then(TapOnLine(overlay, world, touchWidth, onClick))

private data class TapOnLine(
    val overlay: MapOverlayState,
    val world: DoubleArray,
    val touchWidth: Dp,
    val onClick: () -> Unit
) : ModifierNodeElement<TapOnLineNode>() {
    override fun create() = TapOnLineNode(this)
    override fun update(node: TapOnLineNode) {
        node.line = this
    }
}

private class TapOnLineNode(line: TapOnLine) : DelegatingNode(), PointerInputModifierNode {
    var line = line
        set(value) {
            field = value
            pointer.resetPointerInputHandler()
        }

    private val pointer = delegate(
        SuspendingPointerInputModifierNode {
            awaitEachGesture {
                // Every line hears the touch down before any of them acts on it, so the one that
                // takes it can be the nearest rather than whichever happens to lie underneath.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val distance = line.overlay.distanceTo(line.world, down.position)
                line.overlay.offer(down.id, distance, this@TapOnLineNode)
                awaitPointerEvent(PointerEventPass.Main)

                val reach = line.touchWidth.toPx() / 2
                // Consumed already: something with a shape of its own, a marker say, has it.
                if (down.isConsumed || distance > reach) return@awaitEachGesture
                if (!line.overlay.nearest(this@TapOnLineNode)) return@awaitEachGesture
                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                up.consume()
                line.onClick()
            }
        }
    )

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) = pointer.onPointerEvent(pointerEvent, pass, bounds)

    override fun onCancelPointerInput() = pointer.onCancelPointerInput()

    /** Everything below a line is still there to be tapped, which is the whole map. */
    override fun sharePointerInputWithSiblings() = true
}

/**
 * The projection, as an object that outlives a measure pass.
 *
 * [MapView] hands the same instance to the overlay every time it lays out, because the content
 * captured it when it was composed: a fresh one per pass would leave every `Canvas` drawing
 * through a projection that had stopped being updated. The camera inside it is Compose state, so
 * whoever reads it — a marker in layout, a line in draw — is invalidated when the map moves.
 */
internal class MapOverlayState(private val camera: MapCameraState) : MapOverlayScope {
    private var width = 0f
    private var height = 0f
    private var density = 1f

    /** Called from the measure pass, before anything reads the projection. */
    fun measured(width: Float, height: Float, density: Float) {
        this.width = width
        this.height = height
        this.density = density
    }

    override fun project(point: LatLon): Offset {
        val world = Mercator.worldPixels(camera.zoom, density)
        val dx = (Mercator.x(point.lon) - camera.x).nearest
        val dy = Mercator.y(point.lat) - camera.y
        return Offset((dx * world + width / 2).toFloat(), (dy * world + height / 2).toFloat())
    }

    override fun unproject(offset: Offset): LatLon =
        camera.pointAt(offset.x, offset.y, width, height, density)

    override fun Modifier.at(point: LatLon, anchor: Alignment): Modifier = then(Anchor(point, anchor))

    /** Writes [world] — pairs of unit-Mercator x and y — into [path] as it stands now on screen. */
    fun trace(path: Path, world: DoubleArray) {
        if (world.size < 4) return
        val scale = Mercator.worldPixels(camera.zoom, density)
        var x = (world[0] - camera.x).nearest
        path.moveTo(screenX(x, scale), screenY(world[1], scale))
        for (i in 2 until world.size step 2) {
            // Along the line each step is taken the short way round, so a track that crosses the
            // antimeridian carries on rather than shooting back across the whole world.
            val step = world[i] - world[i - 2]
            x += step - round(step)
            path.lineTo(screenX(x, scale), screenY(world[i + 1], scale))
        }
    }

    private var claimant: Any? = null
    private var claimedBy: PointerId? = null
    private var claimedAt = Float.MAX_VALUE

    /** A line saying how far the touch landed from it. The nearest one wins the tap. */
    fun offer(pointer: PointerId, distance: Float, line: Any) {
        if (pointer != claimedBy) {
            claimedBy = pointer
            claimedAt = Float.MAX_VALUE
            claimant = null
        }
        if (distance < claimedAt) {
            claimedAt = distance
            claimant = line
        }
    }

    fun nearest(line: Any) = claimant === line

    /** How far [at] is, in pixels, from the nearest point of the line [world] draws. */
    fun distanceTo(world: DoubleArray, at: Offset): Float {
        if (world.size < 2) return Float.MAX_VALUE
        val scale = Mercator.worldPixels(camera.zoom, density)
        var x = (world[0] - camera.x).nearest
        var from = Offset(screenX(x, scale), screenY(world[1], scale))
        if (world.size < 4) return (at - from).getDistance()
        var nearest = Float.MAX_VALUE
        for (i in 2 until world.size step 2) {
            val step = world[i] - world[i - 2]
            x += step - round(step)
            val to = Offset(screenX(x, scale), screenY(world[i + 1], scale))
            nearest = minOf(nearest, distanceToSegment(at, from, to))
            from = to
        }
        return nearest
    }

    private fun screenX(dx: Double, scale: Double) = (dx * scale + width / 2).toFloat()

    private fun screenY(worldY: Double, scale: Double) =
        ((worldY - camera.y) * scale + height / 2).toFloat()

    /** The world repeats east and west; a point is drawn on the copy nearest the camera. */
    private val Double.nearest get() = this - floor(this + 0.5)
}

/** How far [point] is from the segment [from]-[to], which is what a tap on a line comes down to. */
internal fun distanceToSegment(point: Offset, from: Offset, to: Offset): Float {
    val line = to - from
    val length = line.getDistanceSquared()
    if (length == 0f) return (point - from).getDistance()
    // Where the foot of the perpendicular falls along the segment, kept between its two ends.
    val along = (((point - from).x * line.x + (point - from).y * line.y) / length).coerceIn(0f, 1f)
    return (point - (from + line * along)).getDistance()
}

/** Which coordinate a child hangs off, read back by [MapView] when it places the child. */
internal data class Anchor(val point: LatLon, val alignment: Alignment) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?) = this@Anchor
}
