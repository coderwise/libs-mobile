package com.coderwise.libs.filepicker

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AppKit.NSModalResponseOK
import platform.AppKit.NSOpenPanel
import platform.UniformTypeIdentifiers.UTType

/** Runs the system open panel and returns the chosen path, or null if cancelled. Call on the main thread. */
@OptIn(ExperimentalForeignApi::class)
internal fun chooseMacPath(title: String?, directories: Boolean, extensions: List<String> = emptyList()): String? {
    val panel = NSOpenPanel()
    panel.canChooseFiles = !directories
    panel.canChooseDirectories = directories
    panel.allowsMultipleSelection = false
    title?.let { panel.message = it }
    if (extensions.isNotEmpty()) {
        val types = extensions.mapNotNull { UTType.typeWithFilenameExtension(it.removePrefix(".")) }
        if (types.isNotEmpty()) panel.allowedContentTypes = types
    }
    return if (panel.runModal() == NSModalResponseOK) panel.URL?.path else null
}
