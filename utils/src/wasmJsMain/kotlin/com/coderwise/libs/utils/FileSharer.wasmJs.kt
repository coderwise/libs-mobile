@file:OptIn(ExperimentalWasmJsInterop::class)

package com.coderwise.libs.utils

import kotlin.js.JsAny

// A download is the closest thing a browser has to sharing a file. The blob, the object URL and
// the throwaway anchor are one JS expression rather than typed bindings: Blob takes a JS array of
// JS strings on wasmJs, and building that back up in Kotlin says less than the four lines it wraps.
// The array is filled chunk by chunk as the caller writes, so the document is never one string.
actual fun shareFile(fileName: String, mimeType: String, writeContent: (Appendable) -> Unit) {
    val parts = newParts()
    val sink = object : ChunkedAppendable() {
        override fun emit(text: String) {
            pushPart(parts, text)
        }
    }
    writeContent(sink)
    sink.flush()
    downloadParts(fileName, parts, mimeType)
}

private fun newParts(): JsAny = js("[]")

private fun pushPart(parts: JsAny, chunk: String): Unit = js("{ parts.push(chunk); }")

private fun downloadParts(fileName: String, parts: JsAny, mimeType: String): Unit = js(
    """{
        const url = URL.createObjectURL(new Blob(parts, { type: mimeType }));
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        URL.revokeObjectURL(url);
    }"""
)
