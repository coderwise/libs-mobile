package com.coderwise.libs.filepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader

@Composable
actual fun rememberTextFilePicker(
    extensions: List<String>,
    title: String?,
    onPicked: (String?) -> Unit
): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return remember(extensions) {
        {
            // A detached <input type="file"> is the only file chooser a browser offers, and it has
            // to be clicked from a user gesture -- which is where this ends up being called from.
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            if (extensions.isNotEmpty()) {
                input.accept = extensions.joinToString(",") { ".$it" }
            }
            input.onchange = {
                val file = input.files?.item(0)
                if (file == null) {
                    callback.value(null)
                } else {
                    val reader = FileReader()
                    // readAsText resolves to a JS string, which arrives here as JsAny? rather
                    // than the js target's dynamic.
                    reader.onload = { callback.value((reader.result as? JsString)?.toString()) }
                    reader.onerror = { callback.value(null) }
                    reader.readAsText(file)
                }
                input.remove()
            }
            input.click()
        }
    }
}
