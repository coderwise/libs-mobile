package com.coderwise.libs.imagepicker

/**
 * Picked images are typically shown at modest sizes (hero/thumbnail), so the long edge is capped
 * well below what a phone camera produces. This keeps the encoded bytes small enough to persist as
 * a SQLite BLOB — notably under Android's ~2MB `CursorWindow` per-row limit, which otherwise throws
 * `SQLiteBlobTooBigException` when a full-resolution photo is read back.
 */
internal const val MAX_IMAGE_DIMENSION_PX = 1280
internal const val IMAGE_JPEG_QUALITY = 85

/**
 * Decodes [bytes], scales the longest edge down to [MAX_IMAGE_DIMENSION_PX] when larger, and
 * re-encodes as JPEG. Returns the input unchanged if it is already small enough or cannot be
 * decoded. Implementations run synchronously and should be called off the main thread.
 */
internal expect fun downscaleImageBytes(bytes: ByteArray): ByteArray
