package com.coderwise.libs.sample.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.coderwise.libs.camera.CameraLens
import com.coderwise.libs.camera.rememberCameraController
import com.coderwise.libs.permissions.isGranted
import com.coderwise.libs.permissions.rememberCameraPermissionState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
internal fun CameraExample() {
    var lens by remember { mutableStateOf(CameraLens.Back) }
    val permission = rememberCameraPermissionState()
    // Changing the lens builds a new controller, which releases the old one's sensor first.
    val camera = rememberCameraController(lens)
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf("Nothing captured yet.") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CameraLens.entries.forEach {
                FilterChip(selected = lens == it, onClick = { lens = it }, label = { Text(it.name) })
            }
        }
        when {
            !camera.isSupported -> Text(
                "No camera is available here for the ${lens.name.lowercase()} lens.",
                style = MaterialTheme.typography.bodyMedium
            )
            !permission.status.isGranted -> {
                // Drawing the viewfinder without the grant is a black rectangle and a platform
                // complaint, so it waits for it.
                Text("The camera needs permission before it can be shown.")
                Button(onClick = { permission.launchPermissionRequest() }) { Text("Allow camera") }
            }
            else -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                ) { camera.Viewfinder(Modifier.fillMaxSize()) }
                Button(onClick = {
                    scope.launch {
                        camera.capture()
                            .onSuccess { shot ->
                                photo = runCatching { shot.bytes.decodeToImageBitmap() }.getOrNull()
                                status = "${shot.bytes.size / 1024} kB, ${shot.mimeType}"
                            }
                            .onFailure { status = it.message ?: "The shot did not come back." }
                    }
                }) { Text("Capture") }
            }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        photo?.let {
            Image(
                bitmap = it,
                contentDescription = "The captured photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
            )
        }
        PlatformNote(
            "CameraX on Android and AVFoundation on iOS; desktop and the browser report " +
                "isSupported = false, so the screen has an honest \"no camera\" path instead of " +
                "a dead shutter. The controller owns the sensor, not the permission: asking, " +
                "explaining a refusal and sending the user to settings are :permissions' job, " +
                "and the library declares neither the Android permission nor the Info.plist key. " +
                "A photo comes back as bytes because callers usually forward it — an upload, a " +
                "decoder — rather than show a file."
        )
    }
}
