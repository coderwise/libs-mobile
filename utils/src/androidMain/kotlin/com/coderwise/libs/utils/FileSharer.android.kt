package com.coderwise.libs.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.koin.core.context.GlobalContext
import java.io.File

actual fun shareFile(fileName: String, content: String, mimeType: String) {
    val context: Context = GlobalContext.get().get()
    val file = File(context.cacheDir, fileName).also { it.writeText(content) }
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
