package com.coderwise.libs.utils

import androidx.compose.ui.Modifier

// No mouse pointer to restyle here; the default cursor stays.
actual fun Modifier.horizontalResizeCursor(): Modifier = this

actual fun Modifier.verticalResizeCursor(): Modifier = this
