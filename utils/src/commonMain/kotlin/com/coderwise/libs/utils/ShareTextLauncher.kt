package com.coderwise.libs.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Hands a piece of text to the platform's share affordance: the share sheet on
 * Android and iOS, the system clipboard on desktop and web (which have no
 * share-sheet concept — callers can treat "shared" as "made available to
 * paste").
 *
 * Prefer this over [shareText] in Compose code: it takes the hosting context
 * from the composition instead of a global, and carries a title for the
 * Android chooser.
 */
@Stable
interface ShareTextLauncher {
    fun share(text: String, title: String)
}

@Composable
expect fun rememberShareTextLauncher(): ShareTextLauncher
