package com.coderwise.libs.experiment.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
    width: Dp = 3.dp
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

    Canvas(modifier.fillMaxSize()) {
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

    override fun unproject(offset: Offset): LatLon {
        val world = Mercator.worldPixels(camera.zoom, density)
        val x = (camera.x + (offset.x - width / 2) / world).mod(1.0)
        val y = camera.y + (offset.y - height / 2) / world
        return LatLon(Mercator.lat(y), Mercator.lon(x))
    }

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

    private fun screenX(dx: Double, scale: Double) = (dx * scale + width / 2).toFloat()

    private fun screenY(worldY: Double, scale: Double) =
        ((worldY - camera.y) * scale + height / 2).toFloat()

    /** The world repeats east and west; a point is drawn on the copy nearest the camera. */
    private val Double.nearest get() = this - floor(this + 0.5)
}

/** Which coordinate a child hangs off, read back by [MapView] when it places the child. */
internal data class Anchor(val point: LatLon, val alignment: Alignment) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?) = this@Anchor
}
