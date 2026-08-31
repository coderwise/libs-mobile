package com.coderwise.libs.filepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject

@Composable
actual fun rememberTextFilePicker(
    extensions: List<String>,
    title: String?,
    onPicked: (String?) -> Unit
): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    // The picker holds only a weak reference to its delegate, so something has to own it for as
    // long as the sheet is up; composition does.
    val delegate = remember { TextFilePickerDelegate() }
    delegate.onPicked = { callback.value(it) }
    return remember(delegate, extensions) { { presentDocumentPicker(extensions, delegate) } }
}

private fun presentDocumentPicker(extensions: List<String>, delegate: TextFilePickerDelegate) {
    // public.data as the floor, so a file whose extension the system does not recognise is still
    // selectable rather than greyed out.
    val types = extensions.mapNotNull { UTType.typeWithFilenameExtension(it) } +
        listOfNotNull(UTType.typeWithIdentifier("public.data"))
    val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
    picker.delegate = delegate
    picker.allowsMultipleSelection = false
    @Suppress("DEPRECATION")
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(picker, animated = true, completion = null)
}

private class TextFilePickerDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var onPicked: (String?) -> Unit = {}

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        onPicked((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.readText())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
    }
}

/**
 * A file picked out of Files or iCloud is handed over as a security-scoped URL: it is readable
 * only between the two calls below.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSURL.readText(): String? {
    val scoped = startAccessingSecurityScopedResource()
    return try {
        NSString.stringWithContentsOfURL(this, NSUTF8StringEncoding, null)
    } finally {
        if (scoped) stopAccessingSecurityScopedResource()
    }
}
