package com.coderwise.libs.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor

private val horizontal = PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))
private val vertical = PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR))

actual fun Modifier.horizontalResizeCursor(): Modifier = pointerHoverIcon(horizontal)

actual fun Modifier.verticalResizeCursor(): Modifier = pointerHoverIcon(vertical)
