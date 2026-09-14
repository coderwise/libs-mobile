package com.coderwise.libs.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.koin.core.context.GlobalContext
import java.io.File

actual fun shareFile(fileName: String, mimeType: String, writeContent: (Appendable) -> Unit) {
    val context: Context = GlobalContext.get().get()
    // A BufferedWriter is an Appendable, so the caller's text goes to disk as it is produced.
    val file = File(context.cacheDir, fileName).also { target ->
        target.bufferedWriter().use { writeContent(it) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, fileName).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
