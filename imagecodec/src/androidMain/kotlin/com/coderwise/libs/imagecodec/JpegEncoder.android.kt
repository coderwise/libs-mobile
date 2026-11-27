package com.coderwise.libs.imagecodec

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

actual fun ImageBitmap.encodeToJpeg(quality: Int): ByteArray? = runCatching {
    ByteArrayOutputStream().use { out ->
        if (asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, out)) out.toByteArray() else null
    }
}.getOrNull()
