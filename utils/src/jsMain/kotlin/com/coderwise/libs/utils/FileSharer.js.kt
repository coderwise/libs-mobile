package com.coderwise.libs.utils

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

// A download is the closest thing a browser has to sharing a file, and a Blob is assembled from
// parts — so the chunks the caller wrote go in as they came, without being joined into one string
// first.
actual fun shareFile(fileName: String, mimeType: String, writeContent: (Appendable) -> Unit) {
    val parts = mutableListOf<String>()
    val sink = object : ChunkedAppendable() {
        override fun emit(text: String) {
            parts += text
        }
    }
    writeContent(sink)
    sink.flush()
    val blob = Blob(parts.toTypedArray(), BlobPropertyBag(type = mimeType))
    val url = URL.createObjectURL(blob)
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = url
    anchor.download = fileName
    document.body?.appendChild(anchor)
    anchor.click()
    document.body?.removeChild(anchor)
    URL.revokeObjectURL(url)
}
