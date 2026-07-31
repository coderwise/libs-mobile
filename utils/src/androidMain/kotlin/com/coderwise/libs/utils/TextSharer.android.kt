package com.coderwise.libs.utils

import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext

actual fun shareText(text: String) {
    val context: Context = GlobalContext.get().get()
    // The Koin-supplied context is the application, which has no task to host
    // the chooser in — start one.
    context.startShareChooser(text = text, title = null, newTask = true)
}

internal fun Context.startShareChooser(text: String, title: String?, newTask: Boolean) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (title != null) putExtra(Intent.EXTRA_SUBJECT, title)
        if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(send, title).apply {
        if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(chooser)
}
