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
import kotlin.math.pow
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

    val zoomInt by remember { derivedStateOf { state.zoom.toInt() } }
    val zoomScale by remember { derivedStateOf { 2.0.pow(state.zoom - zoomInt) } }

    val centerFractional by remember {
        derivedStateOf {
            MapMath.latLonToTileFractional(state.latitude, state.longitude, zoomInt.toDouble())
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
                zoomInt = zoomInt,
                zoomScale = zoomScale,
                centerFractional = centerFractional,
                visibleTileRange = visibleTileRange,
                tileContent = tileContent
            )
            TiledMapScope(
                state = state,
                containerSize = containerSize,
                zoomInt = zoomInt,
                zoomScale = zoomScale,
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
 */
@Composable
private fun TileLayer(
    state: TiledMapState,
    zoomInt: Int,
    zoomScale: Double,
    centerFractional: Pair<Double, Double>,
    visibleTileRange: TileRange?,
    tileContent: @Composable (tile: TileId, modifier: Modifier) -> Unit
) {
    Layout(
        modifier = Modifier.graphicsLayer { rotationZ = (-state.bearing).toFloat() },
        content = {
            val tileCount = 1 shl zoomInt
            visibleTileRange?.forEach { tx, ty ->
                if (ty in 0 until tileCount) {
                    val wrappedX = tx.mod(tileCount)
                    val tile = TileId(zoomInt, wrappedX, ty)
                    key(tile.key) {
                        tileContent(tile, Modifier.fillMaxSize())
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val (cfx, cfy) = centerFractional
        val pixelsPerTile = state.tileSizePx * zoomScale
        val centerX = constraints.maxWidth / 2
        val centerY = constraints.maxHeight / 2
        val tileWorldSize = 1 shl zoomInt

        // Safety: never exceed the number of measurables provided by composition
        var measurableIndex = 0
        val tilePlaceables = mutableListOf<Pair<PlaceableInfo, androidx.compose.ui.layout.Placeable>>()

        visibleTileRange?.forEach { tx, ty ->
            if (ty in 0 until tileWorldSize && measurableIndex < measurables.size) {
                val measurable = measurables[measurableIndex++]

                val left = floor((tx - cfx) * pixelsPerTile).toInt()
                val right = floor((tx + 1 - cfx) * pixelsPerTile).toInt()
                val top = floor((ty - cfy) * pixelsPerTile).toInt()
                val bottom = floor((ty + 1 - cfy) * pixelsPerTile).toInt()

                val placeable = measurable.measure(Constraints.fixed(right - left, bottom - top))
                tilePlaceables.add(PlaceableInfo(centerX + left, centerY + top) to placeable)
            }
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            tilePlaceables.forEach { (info, placeable) ->
                placeable.place(info.x, info.y)
            }
        }
    }
}

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
