package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.coderwise.libs.map.TiledMap
import com.coderwise.libs.map.rememberTiledMapState

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

@Preview
@Composable
fun SampleAppPreview() {
    SampleApp()
}
