package com.coderwise.libs.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.coderwise.libs.mapcore.TileId
import com.coderwise.libs.mapcore.MapMath
import kotlin.math.floor
import kotlin.math.roundToInt

@LayoutScopeMarker
@Stable
class TiledMapScope internal constructor(
    private val state: TiledMapState,
    private val containerSize: IntSize,
    private val zoomInt: Int,
    private val zoomScale: Double,
    private val centerFractional: Pair<Double, Double>
) {
    fun Modifier.anchoredAt(latitude: Double, longitude: Double): Modifier {
        return offset {
            val offset = latLonToOffset(latitude, longitude)
            IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
        }
    }

    /**
     * Position of a geo point in absolute pixels at the current zoom, *independent of the pan
     * centre*. Only relative distances between points are meaningful (the origin is the map's
     * top-left, not the viewport) — which is exactly what screen-space label collision needs, and
     * what lets that collision pass depend on zoom alone, so panning stays free of recomputation.
     */
    fun worldPixelOf(latitude: Double, longitude: Double): Offset {
        val (fx, fy) = MapMath.latLonToTileFractional(latitude, longitude, zoomInt.toDouble())
        val unit = (state.tileSizePx * zoomScale).toFloat()
        return Offset(fx.toFloat() * unit, fy.toFloat() * unit)
    }

    fun latLonToOffset(latitude: Double, longitude: Double): Offset {
        val tileCount = 1L shl zoomInt
        val (fx, fy) = MapMath.latLonToTileFractional(latitude, longitude, zoomInt.toDouble())
        val (cfx, cfy) = centerFractional

        // Wrap dx to the shortest distance across the anti-meridian
        val dx = ((fx - cfx + tileCount / 2.0).mod(tileCount.toDouble())) - tileCount / 2.0

        val centerX = containerSize.width / 2.0
        val centerY = containerSize.height / 2.0

        val screen = state.worldToScreen(
            Offset(
                x = (dx * state.tileSizePx * zoomScale).toFloat(),
                y = ((fy - cfy) * state.tileSizePx * zoomScale).toFloat()
            )
        )
        return Offset(
            x = (centerX + screen.x).toFloat(),
            y = (centerY + screen.y).toFloat()
        )
    }
}

/**
 * A pannable, zoomable tiled map.
 *
 * Render tiles via [tileContent] (e.g. [rememberBitmapTileContent]).
 * Overlay arbitrary composables anchored to geographic coordinates using [content]:
 *
 * ```
 * TiledMap(
 *     tiles = tiles,
 *     state = state,
 *     tileContent = rememberBitmapTileContent()
 * ) {
 *     Text("Berlin", modifier = Modifier.anchoredAt(52.5200, 13.4050))
 *     Icon(Icons.Default.LocationOn, modifier = Modifier.anchoredAt(52.5200, 13.4050))
 * }
 * ```
 */
@Composable
fun TiledMap(
    state: TiledMapState,
    tileContent: @Composable (tile: TileId, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    rotateEnabled: Boolean = true,
    content: @Composable TiledMapScope.() -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val centerFractional by remember {
        derivedStateOf {
            MapMath.latLonToTileFractional(state.latitude, state.longitude, state.zoomInt.toDouble())
        }
    }

    val visibleTileRange by remember {
        derivedStateOf {
            calculateVisibleTileRange(containerSize, centerFractional, state)
        }
    }

    Layout(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .then(
                if (interactive) {
                    Modifier
                        .tiledMapTransformControls(
                            state, coroutineScope, containerSize.width, containerSize.height, rotateEnabled
                        )
                        .tiledMapTapControls(state, coroutineScope, containerSize.width, containerSize.height)
                        .tiledMapScrollZoomControls(state, containerSize.width, containerSize.height)
                } else Modifier
            ),
        content = {
            TileLayer(
                state = state,
                visibleTileRange = visibleTileRange,
                tileContent = tileContent
            )
            TiledMapScope(
                state = state,
                containerSize = containerSize,
                zoomInt = state.zoomInt,
                zoomScale = state.zoomScale,
                centerFractional = centerFractional
            ).content()
        }
    ) { measurables, constraints ->
        val tileLayerPlaceable = measurables.first()
            .measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))

        // Measure overlay content
        val contentPlaceables = measurables.subList(1, measurables.size).map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            tileLayerPlaceable.place(0, 0)
            contentPlaceables.forEach { it.place(0, 0) }
        }
    }
}

/**
 * Lays the visible tiles out on the unrotated tile grid and rotates the whole layer as one
 * unit around the viewport center. A single rotated layer rasterizes all tiles together,
 * avoiding the hairline seams that per-tile rotation would produce. Reading [TiledMapState.bearing]
 * inside the graphicsLayer block means bearing changes only update layer properties — no
 * recomposition or relayout.
 *
 * The pan center is read inside the measure block (not taken as a parameter), so panning the map
 * triggers only a re-layout of the existing tile children — never a recomposition of this content.
 * Passing the per-frame-changing center as a composition input would re-run this content every
 * frame, which defeats `key()` reuse and churns (disposes + recreates) the whole tile grid as you
 * drag. Tiles therefore compose once per visible range and are merely re-placed during a pan.
 */
@Composable
private fun TileLayer(
    state: TiledMapState,
    visibleTileRange: TileRange?,
    tileContent: @Composable (tile: TileId, modifier: Modifier) -> Unit
) {
    // state.zoomInt is a derived state read in composition, so the tile list rebuilds only when the
    // integer zoom changes (a boundary) — not on every fractional-zoom frame.
    val zoomInt = state.zoomInt
    // Build the visible tiles into a stable, remembered list, then emit them with `key()` directly:
    // the canonical reuse pattern. Emitting straight from `visibleTileRange?.forEach { if (..) key }`
    // (a nullable safe-call wrapping a custom inline iterator with a per-item conditional) does NOT
    // let Compose match keyed children across recompositions — a one-column range shift then disposes
    // and recreates the WHOLE grid instead of cycling just the edge column. Pre-building keeps the
    // emission a plain `list.forEach { key {} }`, which reuses correctly.
    val tiles = remember(visibleTileRange, zoomInt) {
        val tileCount = 1 shl zoomInt
        buildList {
            visibleTileRange?.forEach { tx, ty ->
                // Key on the unwrapped (tx, ty): when the viewport spans the world-wrap seam (low
                // zoom / wide viewport) two columns share the same wrapped TileId, so keying on
                // tile.key would collide and drop a tile. The wrapped TileId is what tileContent
                // fetches.
                if (ty in 0 until tileCount) add(VisibleTile(tx, ty, TileId(zoomInt, tx.mod(tileCount), ty)))
            }
        }
    }
    Layout(
        modifier = Modifier.graphicsLayer { rotationZ = (-state.bearing).toFloat() },
        content = {
            tiles.forEach { visible ->
                key(visible.tx, visible.ty) {
                    tileContent(visible.tile, Modifier.fillMaxSize())
                }
            }
        }
    ) { measurables, constraints ->
        // Read the pan center and scale here, in the layout phase: snapshot reads of
        // latitude/longitude/zoomScale that invalidate layout (re-place) rather than composition as
        // you pan or zoom.
        val (cfx, cfy) = MapMath.latLonToTileFractional(
            state.latitude, state.longitude, zoomInt.toDouble()
        )
        val pixelsPerTile = state.tileSizePx * state.zoomScale
        val centerX = constraints.maxWidth / 2
        val centerY = constraints.maxHeight / 2

        // `measurables` align 1:1 with `tiles` (same order, same count).
        val tilePlaceables = tiles.indices.mapNotNull { index ->
            if (index >= measurables.size) return@mapNotNull null
            val visible = tiles[index]
            val left = floor((visible.tx - cfx) * pixelsPerTile).toInt()
            val right = floor((visible.tx + 1 - cfx) * pixelsPerTile).toInt()
            val top = floor((visible.ty - cfy) * pixelsPerTile).toInt()
            val bottom = floor((visible.ty + 1 - cfy) * pixelsPerTile).toInt()
            val placeable = measurables[index].measure(Constraints.fixed(right - left, bottom - top))
            PlaceableInfo(centerX + left, centerY + top) to placeable
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            tilePlaceables.forEach { (info, placeable) ->
                placeable.place(info.x, info.y)
            }
        }
    }
}

private data class VisibleTile(val tx: Int, val ty: Int, val tile: TileId)
private data class PlaceableInfo(val x: Int, val y: Int)

private fun calculateVisibleTileRange(
    containerSize: IntSize,
    centerFractional: Pair<Double, Double>,
    state: TiledMapState
): TileRange? {
    if (containerSize.width <= 0 || containerSize.height <= 0) return null

    val (cfx, cfy) = centerFractional
    // A rotated viewport sweeps a larger axis-aligned area of the tile grid
    val (effectiveWidth, effectiveHeight) = state.rotatedViewportSize(
        containerSize.width.toDouble(), containerSize.height.toDouble()
    )
    val halfWidthTiles = (effectiveWidth / 2.0) / state.scaledTileSizePx
    val halfHeightTiles = (effectiveHeight / 2.0) / state.scaledTileSizePx

    return TileRange(
        minX = floor(cfx - halfWidthTiles).toInt() - 1,
        maxX = floor(cfx + halfWidthTiles).toInt() + 1,
        minY = floor(cfy - halfHeightTiles).toInt() - 1,
        maxY = floor(cfy + halfHeightTiles).toInt() + 1
    )
}

// Berlin center tile at zoom 12 is (2200, 1342)
private const val previewLat = 52.5200
private const val previewLon = 13.4050
private const val previewZoom = 12

@Composable
private fun PreviewTileContent(tile: TileId, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = "${tile.x},${tile.y}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp)
        )
    }
}

@Preview
@Composable
private fun TiledMapPreview() {
    MaterialTheme {
        TiledMap(
            state = rememberTiledMapState(
                initialLatitude = previewLat,
                initialLongitude = previewLon,
                initialZoom = previewZoom.toDouble()
            ),
            modifier = Modifier.fillMaxSize(),
            tileContent = { tile, modifier -> PreviewTileContent(tile, modifier) }
        ) {
            Text(
                text = "📍 Berlin",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .anchoredAt(previewLat, previewLon)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
