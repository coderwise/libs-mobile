package com.coderwise.libs.filepicker

import androidx.compose.runtime.Composable

/**
 * Remembers a platform folder picker. Returns a `launch` lambda to wire to a button's click;
 * invoking it opens the system folder chooser and calls [onPicked] with the chosen folder's
 * absolute path, or `null` if the user cancelled.
 *
 * Desktop only (JVM and native macOS), where a program works with real paths. Elsewhere there
 * is no path to hand back, so [onPicked] is called with `null` at once; check
 * [isFolderPickerSupported] to hide the button instead. [title] is shown by the dialog where the
 * platform has room for one; pass a localized string.
 */
@Composable
expect fun rememberFolderPicker(
    title: String? = null,
    onPicked: (String?) -> Unit
): () -> Unit

/** Whether [rememberFolderPicker] opens a chooser on this platform. */
expect val isFolderPickerSupported: Boolean
