package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.location.GpsLocation
import com.coderwise.libs.permissions.PermissionStatus
import com.coderwise.libs.permissions.rememberLocationPermissionState
import kotlinx.coroutines.launch

@Composable
internal fun LocationExample() {
    val permission = rememberLocationPermissionState()
    val provider = rememberLocationProvider()
    val scope = rememberCoroutineScope()
    var latest by remember { mutableStateOf<Result<GpsLocation>?>(null) }
    var watching by remember { mutableStateOf(false) }
    var updates by remember { mutableIntStateOf(0) }

    // The stream lives exactly as long as the switch is on: leaving the page, or turning it off,
    // cancels the collector, and that cancellation is what stops the platform's updates.
    LaunchedEffect(watching) {
        if (watching) {
            updates = 0
            provider.locationUpdates().collect {
                latest = it
                updates++
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoSection("Permission") {
            ReadoutRow("status", permission.status.describe())
            OutlinedButton(onClick = { permission.launchPermissionRequest() }) {
                Text("Request location access")
            }
        }
        DemoSection("Position") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { latest = provider.getCurrentLocation() } }) {
                    Text("Get current location")
                }
                FilterChip(
                    selected = watching,
                    onClick = { watching = !watching },
                    label = { Text(if (watching) "Watching · $updates" else "Watch updates") }
                )
            }
            when (val result = latest) {
                null -> Text("Nothing asked yet.")
                else -> result.fold(
                    onSuccess = { Fix(it) },
                    onFailure = { Text(it.message ?: it::class.simpleName ?: "Failed") }
                )
            }
        }
        PlatformNote(
            "Both calls return a Result rather than throwing, so a refusal is a value to show, " +
                "not a crash to catch. The library declares no permission of its own — the " +
                "sample's manifest and Info.plist do — and asking is the :permissions library's " +
                "job, which is why the request button is above the position and not inside it. " +
                "Android reads the platform LocationManager, iOS CoreLocation (while-in-use " +
                "unless background delivery is switched on), and the browser the Geolocation API. " +
                "Desktop has no location source, so there it always fails."
        )
    }
}

@Composable
private fun Fix(location: GpsLocation) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ReadoutRow("latitude", location.latitude.formatted(5))
        ReadoutRow("longitude", location.longitude.formatted(5))
        ReadoutRow("accuracy", location.accuracy?.let { "±${it.toDouble().formatted(1)} m" } ?: "—")
        ReadoutRow("elevation", location.elevation?.let { "${it.formatted(1)} m" } ?: "—")
        ReadoutRow("speed", location.speed?.let { "${it.toDouble().formatted(1)} m/s" } ?: "—")
        ReadoutRow("bearing", location.bearing?.let { "${it.toDouble().formatted(0)}°" } ?: "—")
    }
}

private fun PermissionStatus.describe(): String = when (this) {
    is PermissionStatus.Granted -> "Granted"
    is PermissionStatus.Denied ->
        if (shouldShowRationale) "Denied — a rationale is still worth showing"
        else "Denied — or not asked yet"
}
