package com.coderwise.libs.desktop

import kotlinx.serialization.Serializable

/** What is remembered of the main window between runs. Shared by every platform's `WindowSettings`. */
@Serializable
internal data class WindowPrefs(
    val width: Float = 0f,
    val height: Float = 0f,
    val hasPosition: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
    val maximized: Boolean = false,
)
