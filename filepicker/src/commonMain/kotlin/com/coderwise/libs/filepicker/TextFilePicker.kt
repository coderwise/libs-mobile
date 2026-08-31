package com.coderwise.libs.filepicker

import androidx.compose.runtime.Composable

/**
 * Remembers a platform text-file picker. Returns a `launch` lambda to wire to a button's click;
 * invoking it opens the system document picker. [onPicked] is called with the chosen file's
 * contents as text, or `null` if the user cancelled or the read failed.
 *
 * Text, and one whole string: this is for the documents an app reads as documents — a GPX backup,
 * a JSON export, a CSV — where the parser wants the whole thing anyway. Anything large enough that
 * holding it twice matters wants a streaming API, and this is not it. For photos, use
 * `:imagepicker`, which hands back bytes.
 *
 * [extensions] filters the chooser where a platform can filter by extension (desktop, web) and is
 * ignored where the system picker does not usefully support it — see the Android note below.
 * [title] is shown only by the desktop dialog; the mobile pickers carry their own chrome. Both are
 * user-facing, so pass a localized string rather than a literal.
 */
@Composable
expect fun rememberTextFilePicker(
    extensions: List<String> = emptyList(),
    title: String? = null,
    onPicked: (String?) -> Unit
): () -> Unit
