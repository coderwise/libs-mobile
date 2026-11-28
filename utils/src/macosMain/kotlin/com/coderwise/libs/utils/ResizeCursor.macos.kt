package com.coderwise.libs.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import platform.AppKit.NSCursor

// Compose for native macOS cannot make a custom PointerIcon (its cursor wrapper is internal), so the
// system cursor is set directly while the pointer is over the element and put back to the arrow when it leaves.
actual fun Modifier.horizontalResizeCursor(): Modifier = cursorOnHover { NSCursor.resizeLeftRightCursor }

actual fun Modifier.verticalResizeCursor(): Modifier = cursorOnHover { NSCursor.resizeUpDownCursor }

private fun Modifier.cursorOnHover(cursor: () -> NSCursor): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            when (awaitPointerEvent().type) {
                // Set again on every move: Compose resets the cursor when other hover icons change.
                PointerEventType.Enter, PointerEventType.Move -> cursor().set()
                PointerEventType.Exit -> NSCursor.arrowCursor.set()
                else -> Unit
            }
        }
    }
}
