package com.coderwise.libs.utils

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.writeData
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
actual fun shareFile(fileName: String, mimeType: String, writeContent: (Appendable) -> Unit) {
    val filePath = NSTemporaryDirectory() + fileName
    // A leftover file from an earlier share of the same name would otherwise be appended to.
    NSFileManager.defaultManager.removeItemAtPath(filePath, null)
    NSFileManager.defaultManager.createFileAtPath(filePath, null, null)
    val handle = NSFileHandle.fileHandleForWritingAtPath(filePath) ?: return
    try {
        val sink = FileHandleAppendable(handle)
        writeContent(sink)
        sink.flush()
    } finally {
        handle.closeFile()
    }
    // The file may well have been written from a background thread -- a backup is too big to
    // write on the one drawing the screen -- and UIKit is only ever touched from the main queue.
    val fileUrl = NSURL.fileURLWithPath(filePath)
    dispatch_async(dispatch_get_main_queue()) {
        val activityVC = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null
        )
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(activityVC, animated = true, completion = null)
    }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class FileHandleAppendable(private val handle: NSFileHandle) : ChunkedAppendable() {
    override fun emit(text: String) {
        val data = NSString.create(string = text)
            .dataUsingEncoding(NSUTF8StringEncoding) ?: return
        handle.writeData(data)
    }
}
