package com.coderwise.libs.sample.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.coderwise.libs.imagepicker.rememberImagePicker
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
internal fun ImagePickerExample() {
    var status by remember { mutableStateOf("Nothing picked yet.") }
    var picked by remember { mutableStateOf<ImageBitmap?>(null) }

    val pickImage = rememberImagePicker { bytes ->
        if (bytes == null) {
            picked = null
            status = "Cancelled, or the image could not be read."
        } else {
            picked = runCatching { bytes.decodeToImageBitmap() }.getOrNull()
            status = "${bytes.size / 1024} kB back from the picker" +
                (picked?.let { ", decoded to ${it.width}×${it.height}" } ?: ", could not decode")
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = pickImage) { Text("Pick an image") }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        picked?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "The picked image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
            )
        }
        PlatformNote(
            "The library downscales before it returns: the long edge is capped at 1280px and " +
                "re-encoded as JPEG, which keeps a phone-camera photo under Android's ~2MB " +
                "per-row CursorWindow limit and so storable as a SQLite BLOB. The kB above is " +
                "what came back after that, not what the camera wrote."
        )
    }
}
