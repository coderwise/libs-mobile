package com.coderwise.libs.utils

import androidx.compose.ui.Modifier

/**
 * Shows the horizontal-resize cursor (↔) while the mouse is over the element: put it on a draggable
 * divider between side-by-side panes. Common Compose has no such cursor, so this picks the platform's own.
 * Platforms without a mouse pointer (Android, iOS) and the web leave the default cursor.
 */
expect fun Modifier.horizontalResizeCursor(): Modifier

/** Shows the vertical-resize cursor (↕) while the mouse is over the element: for a divider between stacked panes. */
expect fun Modifier.verticalResizeCursor(): Modifier
