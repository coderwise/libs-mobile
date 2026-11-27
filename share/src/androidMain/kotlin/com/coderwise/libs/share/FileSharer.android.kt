package com.coderwise.libs.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import org.koin.core.context.GlobalContext
import java.io.File

actual fun shareFile(fileName: String, mimeType: String, writeContent: (Appendable) -> Unit) {
    val context: Context = GlobalContext.get().get()
    val path = Path(context.cacheDir.path, fileName)
    SystemFileSystem.sink(path).buffered().use { sink ->
        // The caller's text goes to disk as it is produced.
        writeContent(object : Appendable {
            override fun append(csq: CharSequence?) = apply { sink.writeString(csq.toString()) }
            override fun append(csq: CharSequence?, start: Int, end: Int) = apply { sink.writeString(csq.toString().substring(start, end)) }
            override fun append(c: Char) = apply { sink.writeString(c.toString()) }
        })
    }
    // FileProvider only takes a java.io.File: the one place a File has to exist.
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path.toString()))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, fileName).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
