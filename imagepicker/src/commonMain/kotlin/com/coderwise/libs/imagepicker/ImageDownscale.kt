package com.coderwise.libs.imagepicker

/**
 * The default cap on a picked image's long edge. Picked images are typically shown at modest sizes
 * (hero/thumbnail), so it sits well below what a phone camera produces. This keeps the encoded
 * bytes small enough to persist as a SQLite BLOB — notably under Android's ~2MB `CursorWindow`
 * per-row limit, which otherwise throws `SQLiteBlobTooBigException` when a full-resolution photo
 * is read back. Callers that keep their pictures as files can pass a larger cap, or null for none,
 * to [rememberImagePicker].
 */
const val MAX_IMAGE_DIMENSION_PX = 1280
internal const val IMAGE_JPEG_QUALITY = 85

/**
 * Decodes [bytes], scales the longest edge down to [maxDimensionPx] when larger, and
 * re-encodes as JPEG. Returns the input unchanged if it is already small enough or cannot be
 * decoded. Implementations run synchronously and should be called off the main thread.
 */
internal expect fun downscaleImageBytes(bytes: ByteArray, maxDimensionPx: Int = MAX_IMAGE_DIMENSION_PX): ByteArray

/**
 * The picked file as handed to the app: [limitImageBytes] to the cap, and the date it was taken,
 * read from [original] because scaling re-encodes and so loses the metadata.
 */
internal fun pickedImage(original: ByteArray, maxDimensionPx: Int?): PickedImage =
    PickedImage(limitImageBytes(original, maxDimensionPx), exifCapturedAt(original))

/** [bytes] as they are for a null cap, else [downscaleImageBytes] to it. */
internal fun limitImageBytes(bytes: ByteArray, maxDimensionPx: Int?): ByteArray =
    if (maxDimensionPx == null) bytes else downscaleImageBytes(bytes, maxDimensionPx)
