package com.coderwise.libs.imagepicker

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The most the system's picker takes at once; asking for more makes it throw. */
private val systemLimit: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        MediaStore.getPickImagesMaxLimit()
    } else {
        Int.MAX_VALUE
    }

@Composable
actual fun rememberPhotoPicker(
    maxDimensionPx: Int?,
    maxItems: Int?,
    onResult: (List<PickedImage>) -> Unit,
): () -> Unit {
    if (LocalInspectionMode.current) return {}
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)

    val deliver = { uris: List<Uri> ->
        if (uris.isEmpty()) {
            currentOnResult(emptyList())
        } else {
            scope.launch {
                val images = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?.let { pickedImage(it, maxDimensionPx) }
                        }.getOrNull()
                    }
                }
                currentOnResult(images)
            }
        }
    }

    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        deliver(listOfNotNull(uri))
    }
    val several = remember(maxItems) {
        // The multiple-choice contract refuses fewer than two, and more than the system allows.
        maxItems?.let { ActivityResultContracts.PickMultipleVisualMedia(it.coerceIn(2, systemLimit)) }
            ?: ActivityResultContracts.PickMultipleVisualMedia()
    }
    val multiple = rememberLauncherForActivityResult(several) { uris -> deliver(uris) }

    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    return { if (maxItems == 1) single.launch(request) else multiple.launch(request) }
}
