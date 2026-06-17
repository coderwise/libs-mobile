package com.coderwise.libs.utils

import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext

actual fun shareText(text: String) {
    val context: Context = GlobalContext.get().get()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
