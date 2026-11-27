package com.coderwise.libs.imagecodec

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Encodes this picture as a JPEG at [quality] (1–100), or returns null if the platform cannot. The
 * result carries no EXIF orientation: it is stored as it looks. The web has no use for it (nothing
 * there outlives the page) and always returns null.
 */
expect fun ImageBitmap.encodeToJpeg(quality: Int = DEFAULT_JPEG_QUALITY): ByteArray?

const val DEFAULT_JPEG_QUALITY = 80
