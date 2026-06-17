package com.coderwise.libs.utils

import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual fun shareFile(fileName: String, content: String, mimeType: String) {
    val filePath = NSTemporaryDirectory() + fileName
    (NSString.create(string = content) as NSString).writeToFile(
        path = filePath,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null
    )
    val fileUrl = NSURL.fileURLWithPath(filePath)
    val activityVC = UIActivityViewController(
        activityItems = listOf(fileUrl),
        applicationActivities = null
    )
    @Suppress("DEPRECATION")
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(activityVC, animated = true, completion = null)
}
