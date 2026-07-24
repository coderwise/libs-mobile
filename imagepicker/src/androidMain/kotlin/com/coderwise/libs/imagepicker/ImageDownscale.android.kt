package com.coderwise.libs.imagepicker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

internal actual fun downscaleImageBytes(bytes: ByteArray): ByteArray = runCatching {
    // Cheap bounds-only pass to learn the source dimensions without allocating the full bitmap.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return bytes
    if (maxOf(srcW, srcH) <= MAX_IMAGE_DIMENSION_PX) return bytes

    // Sub-sample to roughly target size first (keeps peak memory low), then scale exactly.
    var sample = 1
    while (maxOf(srcW, srcH) / (sample * 2) >= MAX_IMAGE_DIMENSION_PX) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return bytes

    val scale = MAX_IMAGE_DIMENSION_PX.toFloat() / maxOf(decoded.width, decoded.height)
    val scaled = if (scale < 1f) {
        decoded.scale(
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
        )
    } else {
        decoded
    }

    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, out)
        out.toByteArray()
    }
}.getOrDefault(bytes)
