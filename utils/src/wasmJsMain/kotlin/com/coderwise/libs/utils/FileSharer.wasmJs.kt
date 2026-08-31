package com.coderwise.libs.utils

// A download is the closest thing a browser has to sharing a file. The blob, the object URL
// and the throwaway anchor are one JS expression here rather than typed bindings: Blob takes a
// JS array of JS strings on wasmJs, and building that back up in Kotlin says less than the
// four lines it wraps.
actual fun shareFile(fileName: String, content: String, mimeType: String) {
    downloadFile(fileName, content, mimeType)
}

private fun downloadFile(fileName: String, content: String, mimeType: String): Unit = js(
    """{
        const url = URL.createObjectURL(new Blob([content], { type: mimeType }));
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        URL.revokeObjectURL(url);
    }"""
)
