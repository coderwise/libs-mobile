package com.coderwise.libs.utils.components

import androidx.compose.runtime.Composable

/**
 * A side-effect that keeps the device screen on while this composable is in the composition.
 */
@Composable
expect fun KeepScreenOn()
