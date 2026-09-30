package com.coderwise.libs.imagepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader

@Composable
actual fun rememberPhotoPicker(
    maxDimensionPx: Int?,
    maxItems: Int?,
    onResult: (List<PickedImage>) -> Unit,
): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "image/*"
        input.multiple = maxItems != 1
        input.onchange = {
            val list = input.files
            val files = (0 until (list?.length ?: 0)).mapNotNull { list?.item(it) }.take(maxItems ?: Int.MAX_VALUE)
            readInOrder(files, maxDimensionPx, emptyList()) { currentOnResult(it) }
            null
        }
        input.click()
    }
}

/** Reads [files] one after another, so the pictures come back in the order chosen. An unreadable file is skipped. */
private fun readInOrder(
    files: List<File>,
    maxDimensionPx: Int?,
    done: List<PickedImage>,
    onDone: (List<PickedImage>) -> Unit,
) {
    val file = files.firstOrNull() ?: return onDone(done)
    val reader = FileReader()
    reader.onload = {
        val array = Int8Array(reader.result as ArrayBuffer)
        val image = pickedImage(ByteArray(array.length) { i -> array[i] }, maxDimensionPx)
        readInOrder(files.drop(1), maxDimensionPx, done + image, onDone)
        null
    }
    reader.onerror = {
        readInOrder(files.drop(1), maxDimensionPx, done, onDone)
        null
    }
    reader.readAsArrayBuffer(file)
}
