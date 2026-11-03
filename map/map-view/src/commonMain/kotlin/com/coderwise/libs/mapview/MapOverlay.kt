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
    /**
     * The camera's zoom: fractional, and following a pinch. For a shape that comes and goes with
     * it — read it where the shape is drawn, in a draw block, so that the shape follows the camera
     * without being recomposed.
     */
    val zoom: Float

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

    /**
     * Appends a subpath to [path] for [world], a shape in the form [toWorld] makes, as it stands on
     * screen now. Every call starts with its own `moveTo`, so several shapes can go into one
     * [Path] — the rings of a polygon, drawn with an even-odd fill, are its holes — and each is
     * taken the short way across the antimeridian rather than back across the whole world.
     *
     * Only the affine is left to do, so tracing is cheap enough for the draw pass: call it there,
     * where it reads the camera and the shape follows the map. Does nothing for fewer than two
     * points.
     */
    fun trace(path: Path, world: DoubleArray)

    /**
     * A tap on a shape of your own, which is a tap within [touchWidth] of it — [distance] says how
     * far, in pixels, a touch at an offset lands from it, and 0 anywhere inside a polygon.
     *
     * The offset is in the map's pixels, the same as [project] gives, so a shape measures itself
     * with the projection it is drawn through. Several shapes can lie over the same pixels: the
     * nearest one wins the tap, not the topmost, and one that is missed hands the tap on to whatever
     * lies under it, the map included. A drag that starts on a shape still pans the map. This is
     * what [Polyline] uses for its own taps.
     *
     * The modifier fills no space and draws nothing, so put it on something that already covers the
     * map, such as the `Canvas` the shape is drawn on.
     */
    fun Modifier.mapTap(
        distance: (Offset) -> Float,
        touchWidth: Dp = 24.dp,
        onClick: () -> Unit
    ): Modifier
}

/**
 * [this] as pairs of unit-Mercator x and y, the form [MapOverlayScope.trace] draws from.
 *
 * Projecting a point is a logarithm and a tangent plus an affine, and only the affine depends on
 * the camera. A shape pays for the hard half once instead of on every frame it is drawn, so keep
 * the result for as long as the points stay the same.
 */
fun List<LatLon>.toWorld(): DoubleArray = DoubleArray(size * 2).also {
    forEachIndexed { i, point ->
        it[i * 2] = Mercator.x(point.lon)
        it[i * 2 + 1] = Mercator.y(point.lat)
    }
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
    val world = remember(points) { points.toWorld() }
    val path = remember { Path() }
    val overlay = this as MapOverlayState
    val distance = remember(overlay, world) { { at: Offset -> overlay.distanceTo(world, at) } }

    val sized = modifier.fillMaxSize()
    Canvas(if (onClick == null) sized else sized.mapTap(distance, touchWidth, onClick)) {
        path.rewind()
        trace(path, world) // reads the camera, so the line redraws as the map moves
        drawPath(
            path = path,
            color = color,
            style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * A tap within reach of a shape, and nothing else.
 *
 * A shape covers the whole map however thin it looks, and Compose stops hit testing at the first
 * sibling it lands on — so a plain `pointerInput` here would make this the only tappable thing on
 * the map, whatever it was drawn over. Sharing the input with its siblings is what lets the tap
 * carry on down to the next shape, and to the map underneath when it misses them all.
 *
 * Only the lift is consumed, and only once the gesture is known to be a tap: a drag that starts on
 * the shape still pans the map, because panning cancels this before the finger comes up.
 */
private data class ShapeTap(
    val overlay: MapOverlayState,
    val distance: (Offset) -> Float,
    val touchWidth: Dp,
    val onClick: () -> Unit
) : ModifierNodeElement<ShapeTapNode>() {
    override fun create() = ShapeTapNode(this)
    override fun update(node: ShapeTapNode) {
        node.tap = this
    }
}

private class ShapeTapNode(tap: ShapeTap) : DelegatingNode(), PointerInputModifierNode {
    var tap = tap
        set(value) {
            field = value
            pointer.resetPointerInputHandler()
        }

    private val pointer = delegate(
        SuspendingPointerInputModifierNode {
            awaitEachGesture {
                // Every shape hears the touch down before any of them acts on it, so the one that
                // takes it can be the nearest rather than whichever happens to lie underneath.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val distance = tap.distance(down.position)
                tap.overlay.offer(down.id, distance, this@ShapeTapNode)
                awaitPointerEvent(PointerEventPass.Main)

                val reach = tap.touchWidth.toPx() / 2
                // Consumed already: something with a shape of its own, a marker say, has it.
                if (down.isConsumed || distance > reach) return@awaitEachGesture
                if (!tap.overlay.nearest(this@ShapeTapNode)) return@awaitEachGesture
                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                up.consume()
                tap.onClick()
            }
        }
    )

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) = pointer.onPointerEvent(pointerEvent, pass, bounds)

    override fun onCancelPointerInput() = pointer.onCancelPointerInput()

    /** Everything below a shape is still there to be tapped, which is the whole map. */
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

    override val zoom: Float get() = camera.zoom

    /** Called from the measure pass, before anything reads the projection. */
    fun measured(width: Float, height: Float, density: Float) {
        this.width = width
        this.height = height
        this.density = density
    }

    override fun project(point: LatLon): Offset = camera.screenPosition(point, width, height, density)

    override fun unproject(offset: Offset): LatLon =
        camera.pointAt(offset.x, offset.y, width, height, density)

    override fun Modifier.at(point: LatLon, anchor: Alignment): Modifier = then(Anchor(point, anchor))

    override fun Modifier.mapTap(
        distance: (Offset) -> Float,
        touchWidth: Dp,
        onClick: () -> Unit
    ): Modifier = then(ShapeTap(this@MapOverlayState, distance, touchWidth, onClick))

    override fun trace(path: Path, world: DoubleArray) {
        if (world.size < 4) return
        val scale = Mercator.worldPixels(camera.zoom, density)
        var x = (world[0] - camera.x).nearest
        val start = screen(x, world[1], scale)
        path.moveTo(start.x, start.y)
        for (i in 2 until world.size step 2) {
            // Along the line each step is taken the short way round, so a track that crosses the
            // antimeridian carries on rather than shooting back across the whole world.
            val step = world[i] - world[i - 2]
            x += step - round(step)
            val to = screen(x, world[i + 1], scale)
            path.lineTo(to.x, to.y)
        }
    }

    private var claimant: Any? = null
    private var claimedBy: PointerId? = null
    private var claimedAt = Float.MAX_VALUE

    /** A shape saying how far the touch landed from it. The nearest one wins the tap. */
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
        var from = screen(x, world[1], scale)
        if (world.size < 4) return (at - from).getDistance()
        var nearest = Float.MAX_VALUE
        for (i in 2 until world.size step 2) {
            val step = world[i] - world[i - 2]
            x += step - round(step)
            val to = screen(x, world[i + 1], scale)
            nearest = minOf(nearest, distanceToSegment(at, from, to))
            from = to
        }
        return nearest
    }

    /** A point of a line, from its offset east of the camera and its absolute world y. */
    private fun screen(dx: Double, worldY: Double, scale: Double): Offset {
        val on = camera.toScreen(dx * scale, (worldY - camera.y) * scale)
        return Offset(on.x + width / 2, on.y + height / 2)
    }

    /** The world repeats east and west; a point is drawn on the copy nearest the camera. */
    private val Double.nearest get() = Mercator.nearest(this)
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
