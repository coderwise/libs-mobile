package com.coderwise.libs.imagepicker

import androidx.compose.runtime.Composable

/**
 * Remembers a platform image picker for one picture. Returns a `launch` lambda to wire to a
 * button's click; invoking it opens the system photo/file picker. [onResult] is called with the
 * chosen image's raw bytes, or `null` if the user cancelled or the read failed.
 *
 * Images whose long edge is over [maxDimensionPx] are scaled down and re-encoded as JPEG; pass
 * null to get the file exactly as it is on disk, metadata and all. The web targets never scale.
 *
 * For the date a picture was taken, or for more than one picture, use [rememberPhotoPicker].
 */
@Composable
fun rememberImagePicker(
    maxDimensionPx: Int? = MAX_IMAGE_DIMENSION_PX,
    onResult: (ByteArray?) -> Unit,
): () -> Unit = rememberPhotoPicker(maxDimensionPx, maxItems = 1) { onResult(it.firstOrNull()?.bytes) }

/**
 * Remembers a platform photo picker. Returns a `launch` lambda to wire to a button's click;
 * invoking it opens the system photo/file picker, and [onResult] is called once with what was
 * chosen, in the order the picker gave it: empty if the user cancelled or nothing could be read.
 * A file that cannot be read is left out and the rest still arrive.
 *
 * [maxItems] is how many pictures may be chosen: 1 for one, null for as many as the system allows.
 * Android's own picker caps it (its limit, often 100), and a picker that cannot take several — the
 * web's and some desktop file dialogs — hands back what it can.
 *
 * Images whose long edge is over [maxDimensionPx] are scaled down, turned upright by their EXIF
 * orientation, and re-encoded as JPEG; pass null to get each file exactly as it is on disk,
 * metadata and all. The web targets never scale. Each [PickedImage] says when its picture was taken.
 */
@Composable
expect fun rememberPhotoPicker(
    maxDimensionPx: Int? = MAX_IMAGE_DIMENSION_PX,
    maxItems: Int? = 1,
    onResult: (List<PickedImage>) -> Unit,
): () -> Unit
