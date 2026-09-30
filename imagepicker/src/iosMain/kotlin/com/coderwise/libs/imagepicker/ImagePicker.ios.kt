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
actual fun rememberPhotoPicker(
    maxDimensionPx: Int?,
    maxItems: Int?,
    onResult: (List<PickedImage>) -> Unit,
): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    // Hold a strong reference to the active delegate; PHPicker keeps only a weak one, so without
    // this the delegate would be collected before the callback fires.
    val delegateHolder = remember { DelegateHolder() }
    return launch@{
        val config = PHPickerConfiguration().apply {
            // Zero means no limit.
            selectionLimit = (maxItems ?: 0).toLong()
            filter = PHPickerFilter.imagesFilter()
        }
        val picker = PHPickerViewController(configuration = config)
        val delegate = ImagePickerDelegate(
            maxDimensionPx = maxDimensionPx,
            onResult = { images ->
                delegateHolder.current = null
                currentOnResult(images)
            },
            dismiss = { picker.dismissViewControllerAnimated(true, completion = null) },
        )
        delegateHolder.current = delegate
        picker.delegate = delegate
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: run {
            delegateHolder.current = null
            currentOnResult(emptyList())
            return@launch
        }
        root.presentViewController(picker, animated = true, completion = null)
    }
}

private class DelegateHolder {
    var current: ImagePickerDelegate? = null
}

private class ImagePickerDelegate(
    private val maxDimensionPx: Int?,
    private val onResult: (List<PickedImage>) -> Unit,
    private val dismiss: () -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        dismiss()
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) {
            onResult(emptyList())
            return
        }
        // Each file loads on its own queue and they finish in any order; the answers are put back in
        // the order chosen, on the main queue, which also keeps the counting single-threaded.
        val images = arrayOfNulls<PickedImage>(results.size)
        var remaining = results.size
        results.forEachIndexed { index, result ->
            result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
                val image = data?.toByteArray()?.let { original ->
                    PickedImage(
                        limitImageBytes(original, maxDimensionPx),
                        exifCapturedAt(original) ?: imageIoCapturedAt(original),
                    )
                }
                dispatch_async(dispatch_get_main_queue()) {
                    images[index] = image
                    remaining -= 1
                    if (remaining == 0) onResult(images.filterNotNull())
                }
            }
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
