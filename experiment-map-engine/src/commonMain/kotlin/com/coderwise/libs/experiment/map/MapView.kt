package com.coderwise.libs.experiment.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
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
     * One composable for every visible tile. [content] is handed a key and draws whatever it likes
     * for it — the tile, a magnified ancestor while that loads, a placeholder, nothing.
     *
     * Every tile of a layer is placed before the first tile of the next one, which is what a layer
     * is for: labels over ground, a route over labels, a marker over everything.
     */
    fun layer(content: @Composable (key: TileKey) -> Unit)
}

/**
 * A tiled map: one composable per visible tile, positioned by this layout.
 *
 * This decides *which* tiles are wanted and *where* they go, and nothing else — it never sees what
 * a tile holds, which is why it is not generic. Nor does it clip a tile to its box: staying inside
 * one is the layer's business, and a label deliberately hangs past the tile that owns it.
 *
 * Nothing here loads anything either. The window it wants is published on [MapState.window] for
 * whoever fills the slots.
 *
 * ```
 * MapView(camera, state, Modifier.fillMaxSize()) {
 *     layer { key -> state.shown(key)?.let { VectorSlot(it.content, it.src) } }
 *     layer { key -> state.shown(key)?.let { VectorLabels(it.content, it.src) } }
 * }
 * ```
 */
@Composable
fun MapView(
    camera: MapCameraState,
    state: MapState<*>,
    modifier: Modifier = Modifier,
    content: MapScope.() -> Unit
) {
    val layers = Layers().apply(content).declared

    DisposableEffect(state) {
        onDispose { state.clearWindow() }
    }

    SubcomposeLayout(modifier.clipToBounds()) { constraints ->
        val grid = tileGrid(
            camera,
            constraints.maxWidth.toFloat(),
            constraints.maxHeight.toFloat(),
            density,
            state
        )
        state.window = grid.window
        val (z, columns, rows, side, originX, originY) = grid
        val count = 1 shl z

        val positions = rows.flatMap { row -> columns.map { column -> column to row } }

        val placeables = layers.flatMapIndexed { layer, content ->
            subcompose(layer) {
                positions.forEach { (column, row) ->
                    key(column, row) {
                        Box(Modifier.fillMaxSize()) {
                            content(TileKey(z, ((column % count) + count) % count, row))
                        }
                    }
                }
            }.mapIndexed { index, measurable ->
                val (column, row) = positions[index]
                val left = floor(column * side - originX).toInt()
                val top = floor(row * side - originY).toInt()
                // Snap to whole pixels the same way on both edges, so neighbours leave no seam.
                val tile = Constraints.fixed(
                    width = ((column + 1) * side - originX).roundToInt() - left,
                    height = ((row + 1) * side - originY).roundToInt() - top
                )
                Triple(measurable.measure(tile), left, top)
            }
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { (placeable, left, top) -> placeable.place(left, top) }
        }
    }
}

/** Collects what the content block declares, in order. */
private class Layers : MapScope {
    val declared = mutableListOf<@Composable (TileKey) -> Unit>()

    override fun layer(content: @Composable (key: TileKey) -> Unit) {
        declared += content
    }
}
