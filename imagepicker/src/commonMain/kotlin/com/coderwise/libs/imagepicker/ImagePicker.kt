package com.coderwise.libs.imagepicker

import androidx.compose.runtime.Composable

/**
 * Remembers a platform image picker. Returns a `launch` lambda to wire to a button's click;
 * invoking it opens the system photo/file picker. [onResult] is called with the chosen image's
 * raw bytes, or `null` if the user cancelled or the read failed.
 *
 * Images whose long edge is over [maxDimensionPx] are scaled down and re-encoded as JPEG; pass
 * null to get the file exactly as it is on disk, metadata and all. The web targets never scale.
 */
@Composable
expect fun rememberImagePicker(
    maxDimensionPx: Int? = MAX_IMAGE_DIMENSION_PX,
    onResult: (ByteArray?) -> Unit,
): () -> Unit
