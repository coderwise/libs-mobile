package com.coderwise.libs.imagepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader

@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "image/*"
        input.onchange = {
            val file = input.files?.item(0)
            if (file == null) {
                currentOnResult(null)
            } else {
                val reader = FileReader()
                reader.onload = {
                    val buffer = reader.result as ArrayBuffer
                    val array = Int8Array(buffer)
                    currentOnResult(ByteArray(array.length) { i -> array[i] })
                    null
                }
                reader.onerror = {
                    currentOnResult(null)
                    null
                }
                reader.readAsArrayBuffer(file)
            }
            null
        }
        input.click()
    }
}
