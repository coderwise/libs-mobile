package com.coderwise.libs.mapview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Which tiles cover the viewport, how big they are on screen and where they start. */
internal data class TileGrid(
    val z: Int,
    val columns: IntRange,
    val rows: IntRange,
    val side: Double,
    val originX: Double,
    val originY: Double
) {
    val window get() = TileWindow(z, columns, rows)
}

/**
 * A tile is 256 dp on screen whatever the density, which is already in the world size. Only the
 * integer part of the zoom picks a level, the fraction scales the tile, which is what keeps a
 * pinch continuous.
 */
internal fun tileGrid(
    camera: MapCameraState,
    width: Float,
    height: Float,
    density: Float,
    state: MapState<*>
): TileGrid {
    val z = floor(camera.zoom).toInt().coerceIn(state.zoomRange.first, state.zoomRange.last)
    val count = 1 shl z
    val world = Mercator.worldPixels(camera.zoom, density)
    val side = world / count
    val originX = camera.x * world - width / 2
    val originY = camera.y * world - height / 2

    val columns = floor(originX / side).toInt()..<ceil((originX + width) / side).toInt()
    val rows = (floor(originY / side).toInt()..<ceil((originY + height) / side).toInt())
        .let { it.first.coerceAtLeast(0)..it.last.coerceAtMost(count - 1) } // the poles end the world

    return TileGrid(z, columns, rows, side, originX, originY)
}

/** What a map is made of: layers of tiles, in the order they are declared. */
@LayoutScopeMarker
interface MapScope {
    /**
     * One composable for every tile of [state] that is visible. [content] is handed a key and
     * draws whatever it likes for it — the tile, a magnified ancestor while that loads, a
     * placeholder, nothing.
     *
     * Every tile of a layer is placed before the first tile of the next one, which is what a layer
     * is for: labels over ground, a route over labels, a marker over everything.
     *
     * Each layer brings its own [state], so a map is as many pyramids as it has sources: a base
     * map to zoom 19 under weather that stops at its own native level and is magnified from there.
     * They are laid out together, each on the level its own source has, and each publishes its own
     * window — so whatever fills it fetches for the layer alone, and stops when the layer goes.
     */
    fun layer(state: MapState<*>, content: @Composable (key: TileKey) -> Unit)

    /**
     * One composable over the whole map, positioned by the map's projection rather than by a tile
     * — a track, a route, the pins on a set of search results. See [MapOverlayScope].
     *
     * It takes its turn among the layers: what is declared after it is drawn over it.
     */
    fun overlay(content: @Composable MapOverlayScope.() -> Unit)
}

/**
 * A tiled map: one composable per visible tile, positioned by this layout.
 *
 * This decides *which* tiles are wanted and *where* they go, and nothing else — it never sees what
 * a tile holds, which is why it is not generic. Nor does it clip a tile to its box: staying inside
 * one is the layer's business, and a label deliberately hangs past the tile that owns it.
 *
 * Nothing here loads anything either. Each layer's window is published on its own
 * [MapState.window] for whoever fills the slots.
 *
 * ```
 * MapView(camera, Modifier.fillMaxSize()) {
 *     layer(ground) { key -> ground.shown(key)?.let { VectorSlot(it.content, it.src) } }
 *     layer(ground) { key -> ground.shown(key)?.let { VectorLabels(it.content, it.src) } }
 *     layer(rain) { key -> rain.shown(key)?.let { RasterSlot(it.content, it.src) } }
 *     overlay {
 *         Polyline(track, color = Color.Blue)
 *         Pin(Modifier.at(destination, Alignment.BottomCenter))
 *     }
 * }
 * ```
 */
@Composable
fun MapView(
    camera: MapCameraState,
    modifier: Modifier = Modifier,
    content: MapScope.() -> Unit
) {
    val slots = Slots().apply(content).declared
    val overlay = remember(camera) { MapOverlayState(camera) }

    // A source is asked for tiles for exactly as long as a layer is drawing it.
    slots.filterIsInstance<Slot.Tiled>().map { it.state }.distinct().forEach { state ->
        key(state) {
            DisposableEffect(state) {
                onDispose { state.clearWindow() }
            }
        }
    }

    SubcomposeLayout(modifier.clipToBounds()) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        overlay.measured(width.toFloat(), height.toFloat(), density)

        val placeables = slots.flatMapIndexed { slot, declared ->
            when (declared) {
                is Slot.Tiled -> {
                    // Every layer is laid out on the level its own source has.
                    val grid = tileGrid(camera, width.toFloat(), height.toFloat(), density, declared.state)
                    declared.state.window = grid.window
                    val (z, columns, rows, side, originX, originY) = grid
                    val count = 1 shl z
                    val positions = rows.flatMap { row -> columns.map { column -> column to row } }

                    subcompose(slot) {
                        positions.forEach { (column, row) ->
                            key(column, row) {
                                Box(Modifier.fillMaxSize()) {
                                    declared.content(TileKey(z, ((column % count) + count) % count, row))
                                }
                            }
                        }
                    }.mapIndexed { index, measurable ->
                        val (column, row) = positions[index]
                        val left = floor(column * side - originX).toInt()
                        val top = floor(row * side - originY).toInt()
                        // Snap to whole pixels the same way on both edges, so neighbours leave no
                        // seam.
                        val tile = Constraints.fixed(
                            width = ((column + 1) * side - originX).roundToInt() - left,
                            height = ((row + 1) * side - originY).roundToInt() - top
                        )
                        Triple(measurable.measure(tile), left, top)
                    }
                }

                is Slot.Over -> subcompose(slot) { declared.content(overlay) }
                    .map { measurable ->
                        val anchor = measurable.parentData as? Anchor
                            // Anything not hung off a coordinate *is* the map: it is measured to
                            // the whole of it, and draws or listens through the projection.
                            ?: return@map Triple(
                                measurable.measure(Constraints.fixed(width, height)), 0, 0
                            )
                        val placeable = measurable.measure(Constraints())
                        val at = overlay.project(anchor.point)
                        val within = anchor.alignment.align(
                            IntSize.Zero,
                            IntSize(placeable.width, placeable.height),
                            layoutDirection
                        )
                        Triple(placeable, (at.x - within.x).roundToInt(), (at.y - within.y).roundToInt())
                    }
            }
        }

        layout(width, height) {
            placeables.forEach { (placeable, left, top) -> placeable.place(left, top) }
        }
    }
}

/** One thing the content block declared: a composable per tile, or one over all of them. */
private sealed interface Slot {
    class Tiled(val state: MapState<*>, val content: @Composable (TileKey) -> Unit) : Slot
    class Over(val content: @Composable MapOverlayScope.() -> Unit) : Slot
}

/** Collects what the content block declares, in order. */
private class Slots : MapScope {
    val declared = mutableListOf<Slot>()

    override fun layer(state: MapState<*>, content: @Composable (key: TileKey) -> Unit) {
        declared += Slot.Tiled(state, content)
    }

    override fun overlay(content: @Composable MapOverlayScope.() -> Unit) {
        declared += Slot.Over(content)
    }
}
