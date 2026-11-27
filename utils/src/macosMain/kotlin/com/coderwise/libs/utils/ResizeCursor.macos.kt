package com.coderwise.libs.utils

import androidx.compose.ui.Modifier

// Compose for native macOS has no resize pointer icon: the pointer stays the default arrow.
actual fun Modifier.horizontalResizeCursor(): Modifier = this

actual fun Modifier.verticalResizeCursor(): Modifier = this
