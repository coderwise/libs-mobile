package com.coderwise.libs.camera

import androidx.compose.runtime.Composable

// Desktop has no camera lane; callers fall back to their other inputs.
@Composable
actual fun rememberCameraController(lens: CameraLens): CameraController = UnsupportedCameraController
