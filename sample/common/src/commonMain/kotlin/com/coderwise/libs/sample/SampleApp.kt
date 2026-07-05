package com.coderwise.libs.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.coderwise.libs.map.TiledMap
import com.coderwise.libs.map.TiledMapScope
import com.coderwise.libs.map.TiledMapState
import com.coderwise.libs.map.rememberTiledMapState
import com.coderwise.libs.mapcore.TileId
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val BERLIN_LAT = 52.5200
private const val BERLIN_LON = 13.4050

/**
 * Demo of the map libraries that runs on every supported platform. Tiles are
 * generated locally (a labelled checkerboard) so the sample needs no network,
 * image loading, or platform code — everything lives in commonMain.
 */
@Composable
fun SampleApp() {
    MaterialTheme {
        val state = rememberTiledMapState(
            initialLatitude = BERLIN_LAT,
            initialLongitude = BERLIN_LON,
            initialZoom = 5.0
        )
        Box(Modifier.fillMaxSize()) {
            TiledMap(
                state = state,
                modifier = Modifier.fillMaxSize(),
                tileContent = { tile, modifier -> CheckerboardTile(tile, modifier) }
            ) {
                CityMarker("Berlin", BERLIN_LAT, BERLIN_LON)
                CityMarker("London", 51.5074, -0.1278)
                CityMarker("Paris", 48.8566, 2.3522)
                CityMarker("New York", 40.7128, -74.0060)
                CityMarker("Tokyo", 35.6762, 139.6503)
            }
            CameraReadout(
                state = state,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            )
            ZoomControls(
                state = state,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            )
        }
    }
}

@Composable
private fun CheckerboardTile(tile: TileId, modifier: Modifier) {
    val even = (tile.x + tile.y) % 2 == 0
    val background =
        if (even) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier
            .background(background)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${tile.zoom}/${tile.x}/${tile.y}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TiledMapScope.CityMarker(
    name: String,
    latitude: Double,
    longitude: Double
) {
    Surface(
        modifier = Modifier.anchoredAt(latitude, longitude),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 2.dp
    ) {
        Text(
            text = "📍 $name",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CameraReadout(state: TiledMapState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 2.dp
    ) {
        Text(
            text = "lat ${state.latitude.rounded()}  lon ${state.longitude.rounded()}  z ${state.zoom.rounded(2)}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ZoomControls(state: TiledMapState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { scope.launch { state.zoomIn() } }) { Text("+") }
        FilledTonalButton(onClick = { scope.launch { state.zoomOut() } }) { Text("−") }
    }
}

/** Multiplatform-safe fixed-decimal formatting (String.format is JVM-only). */
private fun Double.rounded(decimals: Int = 4): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    return ((this * factor).roundToInt() / factor).toString()
}

@Preview
@Composable
fun SampleAppPreview() {
    SampleApp()
}

