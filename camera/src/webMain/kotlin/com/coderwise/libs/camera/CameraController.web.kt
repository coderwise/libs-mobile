package com.coderwise.libs.camera

import androidx.compose.runtime.Composable

// Web could use getUserMedia later; unsupported until there is a reason.
@Composable
actual fun rememberCameraController(lens: CameraLens): CameraController = UnsupportedCameraController
