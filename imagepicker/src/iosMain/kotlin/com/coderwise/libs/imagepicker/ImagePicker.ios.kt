package com.coderwise.libs.imagepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    // Hold a strong reference to the active delegate; PHPicker keeps only a weak one, so without
    // this the delegate would be collected before the callback fires.
    val delegateHolder = remember { DelegateHolder() }
    return launch@{
        val config = PHPickerConfiguration().apply {
            selectionLimit = 1
            filter = PHPickerFilter.imagesFilter()
        }
        val picker = PHPickerViewController(configuration = config)
        val delegate = ImagePickerDelegate(
            onResult = { bytes ->
                delegateHolder.current = null
                currentOnResult(bytes)
            },
            dismiss = { picker.dismissViewControllerAnimated(true, completion = null) },
        )
        delegateHolder.current = delegate
        picker.delegate = delegate
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: run {
            delegateHolder.current = null
            currentOnResult(null)
            return@launch
        }
        root.presentViewController(picker, animated = true, completion = null)
    }
}

private class DelegateHolder {
    var current: ImagePickerDelegate? = null
}

private class ImagePickerDelegate(
    private val onResult: (ByteArray?) -> Unit,
    private val dismiss: () -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        dismiss()
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onResult(null)
            return
        }
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val bytes = data?.toByteArray()?.let(::downscaleImageBytes)
            dispatch_async(dispatch_get_main_queue()) { onResult(bytes) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
