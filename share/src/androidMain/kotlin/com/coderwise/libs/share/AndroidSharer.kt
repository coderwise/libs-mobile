package com.coderwise.libs.share

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shares through the system share sheet. Files go out through a [FileProvider] the app declares with the
 * authority `<applicationId>.fileprovider` and a `cache-path` named `shared` at `shared/`.
 */
class AndroidSharer(private val context: Context) : Sharer {

    override suspend fun share(subject: String?, text: String?, file: ShareFile?) {
        val uri = file?.let { shared ->
            withContext(Dispatchers.IO) {
                // Cache, not files: it is the system's to reclaim, and a shared copy is not worth keeping.
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val copy = File(dir, shared.name + extensionOf(shared.mimeType)).apply { writeBytes(shared.bytes) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
            }
        }
        val send = Intent(Intent.ACTION_SEND).setType(file?.mimeType ?: "text/plain")
        subject?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
        text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        if (uri != null) send.putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // No activity to start from here, hence the new task.
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal fun extensionOf(mimeType: String): String =
    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { ".$it" } ?: ".bin"
