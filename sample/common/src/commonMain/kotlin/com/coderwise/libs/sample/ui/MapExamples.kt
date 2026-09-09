package com.coderwise.libs.sample.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.map.TiledMap
import com.coderwise.libs.map.rememberTiledMapState
import com.coderwise.libs.mapcore.MapMath
import com.coderwise.libs.mapcore.TileId
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class City(val name: String, val latitude: Double, val longitude: Double)

private val CITIES = listOf(
    City("Berlin", 52.5200, 13.4050),
    City("London", 51.5074, -0.1278),
    City("Paris", 48.8566, 2.3522),
    City("New York", 40.7128, -74.0060),
    City("Tokyo", 35.6762, 139.6503)
)

/** The examples that run everywhere, Wasm included — the map modules, new and previous. */
internal fun mapExamples(): List<Example> = listOf(
    Example(
        title = "Map view",
        module = ":map-view",
        summary = "A MapView of layered tile slots, a queue that fills them, and a magnified " +
            "ancestor standing in until a tile lands — with two routes, four search results " +
            "and a pin dropped wherever the map is tapped over the top. On real OpenStreetMap " +
            "tiles, because the engine fetches nothing itself: supplying the tiles is the " +
            "part worth showing.",
        fillsScreen = true
    ) { MapViewExample() },
    Example(
        title = "Vector map",
        module = ":map-view-tiles-vector",
        summary = "The same MapView and the same queue, with a slot that draws decoded geometry " +
            "instead of a bitmap — so the map knows what it is drawing. Tap it and the tile says " +
            "what is under your finger, and the water toggle shows the coastline a tile carries " +
            "for anything drawn over the sea.",
        fillsScreen = true
    ) { VectorMapExample() },
    Example(
        title = "Tiled map",
        module = ":map-engine",
        summary = "A pannable, zoomable, rotatable map with markers anchored to coordinates. " +
            "Tiles are generated locally as a labelled checkerboard, so the sample needs no " +
            "network, tile server, or API key.",
        fillsScreen = true
    ) { TiledMapExample() },
    Example(
        title = "Tile math",
        module = ":map-core",
        summary = "The slippy-map conversions the engine is built on, with no Compose in " +
            "sight: latitude/longitude to tile coordinates and back, and the packed TileId " +
            "that keys every cache."
    ) { TileMathExample() }
)

@Composable
private fun TiledMapExample() {
    val state = rememberTiledMapState(
        initialLatitude = CITIES.first().latitude,
        initialLongitude = CITIES.first().longitude,
        initialZoom = 5.0
    )
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        TiledMap(
            state = state,
            modifier = Modifier.fillMaxSize(),
            tileContent = { tile, modifier -> CheckerboardTile(tile, modifier) }
        ) {
            CITIES.forEach { CityMarker(it.name, it.latitude, it.longitude) }
        }
        CameraReadout(
            state = state,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )
        // Centre-right, not bottom-right: the city row below spans the width, and a chip row
        // laid over the zoom buttons would swallow their taps.
        ZoomControls(
            state = state,
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp)
        )
        // animateLocationTo is the camera API an app drives from its own UI — a search result,
        // a "locate me" button — rather than from a gesture.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CITIES.forEach { city ->
                AssistChip(
                    onClick = {
                        scope.launch {
                            state.animateLocationTo(city.latitude, city.longitude, zoom = 8.0)
                        }
                    },
                    label = { Text(city.name) }
                )
            }
        }
    }
}

@Composable
private fun TileMathExample() {
    var latitude by remember { mutableStateOf(52.5200f) }
    var longitude by remember { mutableStateOf(13.4050f) }
    var zoom by remember { mutableStateOf(10f) }

    val zoomInt = zoom.roundToInt()
    val (tileX, tileY) = MapMath.latLonToTile(latitude.toDouble(), longitude.toDouble(), zoomInt)
    val (fractionalX, fractionalY) =
        MapMath.latLonToTileFractional(latitude.toDouble(), longitude.toDouble(), zoom.toDouble())
    val (cornerLat, cornerLon) =
        MapMath.tileToLatLon(tileX.toDouble(), tileY.toDouble(), zoomInt.toDouble())
    val tile = TileId(zoomInt, tileX, tileY)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoSection("Input") {
            LabelledSlider("Latitude", latitude.toDouble().formatted(), latitude, -85f..85f) {
                latitude = it
            }
            LabelledSlider("Longitude", longitude.toDouble().formatted(), longitude, -180f..180f) {
                longitude = it
            }
            LabelledSlider("Zoom", zoomInt.toString(), zoom, 0f..18f, steps = 17) { zoom = it }
        }
        DemoSection("MapMath") {
            ReadoutRow("latLonToTile", "$tileX / $tileY")
            ReadoutRow(
                "latLonToTileFractional",
                "${fractionalX.formatted(3)} / ${fractionalY.formatted(3)}"
            )
            ReadoutRow(
                "tileToLatLon (NW corner)",
                "${cornerLat.formatted()}, ${cornerLon.formatted()}"
            )
        }
        DemoSection("TileId") {
            ReadoutRow("packed key", tile.key.toString())
            ReadoutRow("unpacked", "${tile.zoom}/${tile.x}/${tile.y}")
            PlatformNote(
                "TileId is a value class over that single Long, so it erases to a bare Long at " +
                    "runtime: the same number keys the in-memory cache, the SQLite tile cache, " +
                    "and the Compose key() of the tile that draws it."
            )
        }
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        ReadoutRow(label, value)
        Slider(
            value = sliderValue,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
