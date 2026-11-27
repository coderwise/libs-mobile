package com.coderwise.libs.share

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Saves pictures to the device's Pictures, in an [album] folder. */
class AndroidGallerySaver(private val context: Context, private val album: String) : GallerySaver {

    /** Needs scoped storage (Android 10), which is what lets an app add to Pictures without a permission. */
    override suspend fun saveToGallery(file: ShareFile) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw UnsupportedOperationException("Needs Android 10")
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name + extensionOf(file.mimeType))
                put(MediaStore.Images.Media.MIME_TYPE, file.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$album")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("No place to save to")
            try {
                resolver.openOutputStream(uri)?.use { it.write(file.bytes) } ?: throw IOException("Cannot write")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: IOException) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
    }
}
