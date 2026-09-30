package com.coderwise.libs.imagepicker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

internal actual fun downscaleImageBytes(bytes: ByteArray, maxDimensionPx: Int): ByteArray = runCatching {
    // Cheap bounds-only pass to learn the source dimensions without allocating the full bitmap.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return bytes
    if (maxOf(srcW, srcH) <= maxDimensionPx) return bytes

    // Sub-sample to roughly target size first (keeps peak memory low), then scale exactly.
    var sample = 1
    while (maxOf(srcW, srcH) / (sample * 2) >= maxDimensionPx) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return bytes

    // BitmapFactory ignores the EXIF orientation and the re-encode drops it, so the picture has to be
    // turned here or it would come out on its side. Turning and scaling are one transform.
    val orientation = Orientation.of(exifOrientation(bytes))
    val scale = minOf(1f, maxDimensionPx.toFloat() / maxOf(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        if (orientation.mirrored) postScale(-1f, 1f)
        postRotate(orientation.degrees)
        postScale(scale, scale)
    }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

    ByteArrayOutputStream().use { out ->
        upright.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, out)
        out.toByteArray()
    }
}.getOrDefault(bytes)
