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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

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
 * How much unturned map has to be laid out for a turned viewport to be full of it: the bounding
 * box, in map axes, of a [width] x [height] rectangle rotated by [bearing]. At 45 degrees that is
 * about twice the area, and twice the tiles — the price of turning a map.
 */
internal fun planeSize(width: Int, height: Int, bearing: Float): IntSize {
    if (bearing == 0f) return IntSize(width, height)
    val radians = bearing * PI / 180.0
    val c = abs(cos(radians))
    val s = abs(sin(radians))
    return IntSize(
        (width * c + height * s).toInt() + 1,
        (width * s + height * c).toInt() + 1
    )
}

/**
 * A tile is 256 dp on screen whatever the density, which is already in the world size. Only the
 * integer part of the zoom picks a level, the fraction scales the tile, which is what keeps a
 * pinch continuous.
 *
 * [width] and [height] are the map plane's, not the viewport's: with a bearing they differ.
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
 *     layer(ground) { key -> ground.shown(key)?.let { VectorSlot(it.content, it.src, it.key.z) } }
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
    // What each declared slot subcomposes, kept from frame to frame — see [Held].
    val held = remember(camera) { mutableMapOf<Int, Held>() }

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

        val plane = planeSize(width, height, camera.bearing)
        // A slot the content block no longer declares takes its holder with it.
        if (held.size > slots.size) held.keys.retainAll { it < slots.size }

        val placeables = slots.flatMapIndexed { slot, declared ->
            when (declared) {
                is Slot.Tiled -> {
                    // Every layer is laid out on the level its own source has, over a plane big
                    // enough to fill the viewport once it is turned. Only *which* tiles is settled
                    // here; where they go the plane works out for itself, in its own measure block,
                    // which is what keeps a pan out of composition entirely.
                    val window = tileGrid(
                        camera, plane.width.toFloat(), plane.height.toFloat(), density, declared.state
                    ).window
                    declared.state.window = window
                    val body = held.plane(slot, declared.state, camera).body(window, declared.content)
                    subcompose(slot, body)
                        .map { measurable ->
                            Placed(
                                placeable = measurable.measure(Constraints.fixed(plane.width, plane.height)),
                                left = (width - plane.width) / 2,
                                top = (height - plane.height) / 2,
                                turned = true
                            )
                        }
                }

                is Slot.Over -> subcompose(slot, held.over(slot, overlay).body(declared.content))
                    .map { measurable ->
                        val anchor = measurable.parentData as? Anchor
                            // Anything not hung off a coordinate *is* the map: it is measured to
                            // the whole of it, and draws or listens through the projection.
                            ?: return@map Placed(
                                measurable.measure(Constraints.fixed(width, height)), 0, 0
                            )
                        val placeable = measurable.measure(Constraints())
                        val at = overlay.project(anchor.point)
                        val within = anchor.alignment.align(
                            IntSize.Zero,
                            IntSize(placeable.width, placeable.height),
                            layoutDirection
                        )
                        Placed(placeable, (at.x - within.x).roundToInt(), (at.y - within.y).roundToInt())
                    }
            }
        }

        layout(width, height) {
            placeables.forEach { (placeable, left, top, turned) ->
                // The plane is centred on the viewport, so turning it about its own middle turns
                // it about the point the camera is looking at. Overlays are not turned: a pin
                // stays upright, and what should follow the map asks the projection where to go.
                if (turned) {
                    placeable.placeWithLayer(left, top) { rotationZ = -camera.bearing }
                } else {
                    placeable.place(left, top)
                }
            }
        }
    }
}

/**
 * One tile layer: the tiles of one source, laid out on the unturned map plane.
 *
 * [window] says which tiles, and is the only thing here that composition depends on — so the layer
 * re-composes when the set of tiles changes and at no other time. Where the tiles go is read from
 * the camera in the measure block below, so a pan re-places children that are already composed and
 * already drawn.
 */
@Composable
private fun TilePlane(
    state: MapState<*>,
    camera: MapCameraState,
    window: TileWindow,
    content: @Composable (TileKey) -> Unit
) {
    val cells = remember(window) { cellsOf(window) }

    Layout(
        content = {
            cells.forEach { cell ->
                key(cell.slotX, cell.slotY) {
                    Box(Modifier.fillMaxSize()) { content(cell.key) }
                }
            }
        }
    ) { measurables, constraints ->
        // The camera is read here, in the layout phase: panning invalidates this measure and
        // nothing above it, so the frame costs a re-placement rather than a recomposition.
        val grid = tileGrid(
            camera, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density, state
        )
        // One size for every tile, at every pan offset.
        //
        // Sizing a tile from the gap between its own two snapped edges looks like the careful thing
        // to do — the tiles then cover the plane exactly — but that gap flips by a pixel as the pan
        // crosses a boundary whenever the spacing is not a whole number of pixels, which it is only
        // at an exactly integer zoom on an integer density. So on nearly every pan, every tile
        // changed size on nearly every frame — and a tile that changes size is re-measured, which
        // invalidates its drawing, which means the grid re-traced every stroke of every tile on the
        // UI thread every frame instead of moving layers it had already drawn.
        //
        // Rounding up gives every tile one size for the whole gesture. Neighbours then overlap by
        // under a pixel, which does not show: a tile paints its own background first.
        val side = measuredTileSize(grid.side)
        val box = Constraints.fixed(side, side)
        val placed = measurables.mapIndexed { index, measurable ->
            val cell = cells[index]
            Triple(
                measurable.measure(box),
                floor(cell.column * grid.side - grid.originX).toInt(),
                floor(cell.row * grid.side - grid.originY).toInt()
            )
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { (placeable, left, top) -> placeable.place(left, top) }
        }
    }
}

/** One cell of a plane: where it sits on the grid, which tile that is, and which slot draws it. */
private class TileCell(
    val slotX: Int,
    val slotY: Int,
    val column: Int,
    val row: Int,
    val key: TileKey
)

/**
 * The cells of [window], in an order a pan does not disturb.
 *
 * The obvious emission — every column of every row, in reading order — reorders itself the moment
 * the window shifts by a row: the same tiles come out one place earlier than they did, and Compose
 * reconciles that by moving every keyed group in the slot table. That is an arraycopy per tile and
 * then a re-measure of everything that moved, and text is the expensive half of it — a label whose
 * group moved loses its paragraph and lays the words out again.
 *
 * So a cell is keyed by its place on a torus: the column modulo the number of columns, which does
 * not change when the window shifts. A tile still on screen keeps its slot, its key and its place
 * in the emission, and the only slots handed a different tile are the ones whose row or column has
 * just scrolled off the far side.
 */
private fun cellsOf(window: TileWindow): List<TileCell> {
    val across = window.x.count()
    val down = window.y.count()
    if (across == 0 || down == 0) return emptyList()

    val columns = IntArray(across).also { slots -> window.x.forEach { slots[it.mod(across)] = it } }
    val rows = IntArray(down).also { slots -> window.y.forEach { slots[it.mod(down)] = it } }
    val count = 1 shl window.z

    return buildList(across * down) {
        for (slotY in 0..<down) {
            for (slotX in 0..<across) {
                val column = columns[slotX]
                val row = rows[slotY]
                add(TileCell(slotX, slotY, column, row, TileKey(window.z, column.mod(count), row)))
            }
        }
    }
}

/**
 * The size every tile of a grid spaced [side] pixels apart is measured at.
 *
 * A function of the spacing alone and not of where the pan happens to sit — which is the whole
 * point of it, and why it is worth a name of its own. Rounded up rather than down so the grid never
 * leaves a gap between neighbours; the sub-pixel overlap it trades for does not show, because a
 * tile paints its own background before anything else.
 */
internal fun measuredTileSize(side: Double): Int = ceil(side).toInt()

/**
 * What one declared slot subcomposes, kept from frame to frame.
 *
 * The point of it is [body]'s *identity*. `subcompose` compares the composable it is handed
 * against the one it composed last by identity, and recomposes the whole subtree when they differ
 * — and a lambda written inline in a measure block is a new one every time that block runs, which
 * during a pan is every frame. That recomposed every tile of every layer on every frame, and
 * re-recorded every tile's drawing with it: the pan that is supposed to cost a layer offset was
 * being spent tracing paths on the UI thread.
 *
 * So the lambda is made once and handed back unchanged for as long as nothing it composes has
 * changed — for a tile layer, the set of tiles and the composable that draws one, and emphatically
 * not the pixel the grid happens to start at.
 */
private sealed class Held {

    class Plane(val state: MapState<*>, private val camera: MapCameraState) : Held() {
        private var window: TileWindow? = null
        private var content: (@Composable (TileKey) -> Unit)? = null
        private var body: (@Composable () -> Unit)? = null

        fun body(
            window: TileWindow,
            content: @Composable (TileKey) -> Unit
        ): @Composable () -> Unit {
            body?.let { if (window == this.window && content === this.content) return it }
            this.window = window
            this.content = content
            return (@Composable { TilePlane(state, camera, window, content) }).also { body = it }
        }
    }

    class Over(private val overlay: MapOverlayState) : Held() {
        private var content: (@Composable MapOverlayScope.() -> Unit)? = null
        private var body: (@Composable () -> Unit)? = null

        fun body(content: @Composable MapOverlayScope.() -> Unit): @Composable () -> Unit {
            body?.let { if (content === this.content) return it }
            this.content = content
            return (@Composable { overlay.content() }).also { body = it }
        }
    }
}

/** The holder for slot [slot], made on first use and replaced only if the layer's source changes. */
private fun MutableMap<Int, Held>.plane(
    slot: Int,
    state: MapState<*>,
    camera: MapCameraState
): Held.Plane = (this[slot] as? Held.Plane)?.takeIf { it.state === state }
    ?: Held.Plane(state, camera).also { this[slot] = it }

private fun MutableMap<Int, Held>.over(slot: Int, overlay: MapOverlayState): Held.Over =
    this[slot] as? Held.Over ?: Held.Over(overlay).also { this[slot] = it }

/** Where one child ends up, and whether it turns with the map. */
private data class Placed(
    val placeable: Placeable,
    val left: Int,
    val top: Int,
    val turned: Boolean = false
)

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
