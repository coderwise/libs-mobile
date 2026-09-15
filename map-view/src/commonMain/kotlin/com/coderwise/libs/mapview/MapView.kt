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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
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

/**
 * How far apart the tiles of level [z] sit on screen at [zoom].
 *
 * A function of the zoom alone, and the one place it is worked out: the grid, the plane that lays
 * the tiles out and the layer that pans them all have to agree on it to the last bit, or the plane
 * drifts out from under the window it was laid out for.
 */
internal fun tileSpacing(zoom: Float, z: Int, density: Float): Double =
    Mercator.worldPixels(zoom, density) / (1 shl z)

/**
 * Where the plane laid out for [window] has to sit now, as the layer holding it has to move.
 *
 * The grid is laid out at its own origin — the window's first column at zero — and this is the
 * whole of what a pan does to it until the window changes. It is read in the draw phase, so it
 * allocates one [Offset] and touches no ranges; and it is in screen axes rather than map axes,
 * because a layer translation is, and the same layer turns the plane.
 */
internal fun panTranslation(
    camera: MapCameraState,
    window: TileWindow,
    width: Float,
    height: Float,
    density: Float
): Offset {
    val world = Mercator.worldPixels(camera.zoom, density)
    val side = tileSpacing(camera.zoom, window.z, density)
    return camera.toScreen(
        window.x.first * side - (camera.x * world - width / 2),
        window.y.first * side - (camera.y * world - height / 2)
    )
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
 * Which tiles cover a [width] x [height] map plane.
 *
 * A tile is 256 dp on screen whatever the density, which is already in the world size. Only the
 * integer part of the zoom picks a level, the fraction scales the tile, which is what keeps a
 * pinch continuous.
 *
 * [width] and [height] are the map plane's, not the viewport's: with a bearing they differ.
 */
internal fun tileWindow(
    camera: MapCameraState,
    width: Float,
    height: Float,
    density: Float,
    state: MapState<*>
): TileWindow {
    val z = floor(camera.zoom).toInt().coerceIn(state.zoomRange.first, state.zoomRange.last)
    val count = 1 shl z
    val world = Mercator.worldPixels(camera.zoom, density)
    val side = tileSpacing(camera.zoom, z, density)
    val originX = camera.x * world - width / 2
    val originY = camera.y * world - height / 2

    val columns = floor(originX / side).toInt()..<ceil((originX + width) / side).toInt()
    val rows = (floor(originY / side).toInt()..<ceil((originY + height) / side).toInt())
        .let { it.first.coerceAtLeast(0)..it.last.coerceAtMost(count - 1) } // the poles end the world

    return TileWindow(z, columns, rows)
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
        // Bound out here, where `density` is the layout's: a layer block brings a density of its
        // own, and the camera reads belong to the layer's observation scope rather than this one.
        val panned = { window: TileWindow ->
            panTranslation(camera, window, plane.width.toFloat(), plane.height.toFloat(), density)
        }
        // A slot the content block no longer declares takes its holder with it.
        if (held.size > slots.size) held.keys.retainAll { it < slots.size }

        val placeables = slots.flatMapIndexed { slot, declared ->
            when (declared) {
                is Slot.Tiled -> {
                    // Every layer is laid out on the level its own source has, over a plane big
                    // enough to fill the viewport once it is turned. Only *which* tiles is settled
                    // here — which is what keeps a pan out of composition. The plane lays them out
                    // relative to this window in its own measure block, and where the window itself
                    // has got to is a property of the layer below, so a pan reaches neither.
                    val window = tileWindow(
                        camera, plane.width.toFloat(), plane.height.toFloat(), density, declared.state
                    )
                    declared.state.window = window
                    val body = held.plane(slot, declared.state, camera).body(window, declared.content)
                    subcompose(slot, body)
                        .map { measurable ->
                            Placed(
                                placeable = measurable.measure(Constraints.fixed(plane.width, plane.height)),
                                left = (width - plane.width) / 2,
                                top = (height - plane.height) / 2,
                                window = window
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
            placeables.forEach { (placeable, left, top, window) ->
                // The plane is centred on the viewport, so turning it about its own middle turns
                // it about the point the camera is looking at. Overlays are not turned: a pin
                // stays upright, and what should follow the map asks the projection where to go.
                if (window == null) {
                    placeable.place(left, top)
                    return@forEach
                }
                placeable.placeWithLayer(left, top) {
                    rotationZ = -camera.bearing
                    // And the pan, as a layer property rather than a position.
                    //
                    // This block runs in the draw phase: a state read in it re-runs the block and
                    // sets the layer's properties, and stops there. Nothing above is re-measured,
                    // nothing below is re-placed, and no display list is recorded again — a drag
                    // moves two floats on a render node and the map moves with them, which is what
                    // a map engine does with its camera matrix.
                    //
                    // The alternative, and what this used to be, is a pan baked into every tile's
                    // position. That re-places every tile of every layer on every frame, and a
                    // child moved without a layer of its own invalidates the layer above it, so
                    // the plane re-recorded its whole draw each time as well.
                    val at = panned(window)
                    translationX = at.x
                    translationY = at.y
                }
            }
        }
    }
}

/**
 * One tile layer: the tiles of one source, laid out on the unturned map plane.
 *
 * [window] says which tiles, and is the only thing here that composition depends on — so the layer
 * re-composes when the set of tiles changes and at no other time.
 *
 * The grid is laid out at its own origin: the window's first column and row at zero, every tile a
 * whole number of spacings from there. Where that origin has got to is the business of the layer
 * above — see [panTranslation] — so nothing here is a function of where the pan sits, and a drag
 * touches neither this measure block nor anything it places.
 */
@Composable
private fun TilePlane(
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
        // The zoom is read here and the pan is not, which is the whole point: a drag leaves this
        // measure valid, and only a pinch or a new window re-runs it.
        val spacing = tileSpacing(camera.zoom, window.z, density)
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
        // Over-zoomed far enough past its own level, a tile's box outgrows what layout can hold:
        // a source that stops at z14 seen at zoom 20 spaces its tiles 2^6 * 256 dp apart, which on
        // a 2x screen is 32768 px and more than [Constraints] can represent in both axes at once.
        // So the tile is measured at a fraction of its box and blown back up by the layer it is
        // placed in — the same pixels either way, since a tile draws to whatever size it is given.
        val magnification = tileMagnification(spacing)
        val side = measuredTileSize(spacing / magnification)
        val box = Constraints.fixed(side, side)
        val placed = measurables.mapIndexed { index, measurable ->
            val cell = cells[index]
            Triple(
                measurable.measure(box),
                gridStep(cell.column - window.x.first, spacing),
                gridStep(cell.row - window.y.first, spacing)
            )
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { (placeable, left, top) ->
                if (magnification == 1.0) {
                    placeable.place(left, top)
                } else {
                    placeable.placeWithLayer(left, top) {
                        // From the tile's own corner, which is the point the grid placed.
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = magnification.toFloat()
                        scaleY = magnification.toFloat()
                    }
                }
            }
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
 * Where the [step]th tile of a grid spaced [side] pixels apart is placed, counting from the grid's
 * own origin — which is the window's first column, not the viewport's edge, because the pan
 * between the two is a layer translation rather than a position.
 *
 * Rounded *up*, and that is the whole of the reason it has a name. The grid's far edge sits at a
 * fraction of a pixel — the translation carrying the pan is not a whole number — so rounding a
 * tile's position down leaves the last one a fraction of a pixel short of the plane's edge and the
 * map shows a sliver of nothing along it. Rounding up cannot: every tile is at least as far along
 * as the true grid puts it, and [measuredTileSize] is wide enough that the one behind still reaches
 * it.
 */
internal fun gridStep(step: Int, side: Double): Int = ceil(step * side).toInt()

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
 * The largest box a tile is measured in. Past this it is measured smaller and scaled up — see
 * [tileMagnification].
 *
 * [Constraints] is what forces the scaling to exist at all — it holds 2^15 - 1 in each axis, and
 * an over-zoomed grid outgrows that — but it is not what sets this number. A slot that caches its
 * drawing rasterises into a texture of the size it was measured at, so the box is also a bill in
 * graphics memory: 64 MB a tile here, and four times that at the 8192 a GPU would still accept.
 * Hence a cap set by what a tile costs to hold rather than by what layout can express.
 */
private const val MAX_TILE_SIDE_PX = 4096

/**
 * How much bigger a tile is drawn than it is measured: 1 for anything that fits in
 * [MAX_TILE_SIDE_PX], and otherwise the power of two that brings it back inside.
 *
 * A power of two so the box a tile is measured in stays one of the sizes it would have been
 * measured at anyway, and so the scale is exact in binary: the grid is placed at the true spacing
 * and the drawn tile has to reach exactly as far.
 */
internal fun tileMagnification(spacing: Double): Double {
    var magnification = 1.0
    while (spacing / magnification > MAX_TILE_SIDE_PX) magnification *= 2.0
    return magnification
}

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
            return (@Composable { TilePlane(camera, window, content) }).also { body = it }
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

/**
 * Where one child ends up, and — for a tile plane — the window it was laid out for, which is what
 * the pan is measured against. An overlay has no window and does not turn with the map.
 */
private data class Placed(
    val placeable: Placeable,
    val left: Int,
    val top: Int,
    val window: TileWindow? = null
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
