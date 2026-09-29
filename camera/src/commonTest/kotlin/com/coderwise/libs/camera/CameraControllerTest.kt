package com.coderwise.libs.camera

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraControllerTest {

    @Test
    fun `the unsupported controller never claims support`() {
        assertFalse(UnsupportedCameraController.isSupported)
    }

    @Test
    fun `the unsupported controller fails instead of throwing`() = runTest {
        val result = UnsupportedCameraController.capture()
        assertTrue(result.isFailure, "capture must report failure, not throw")
    }
}
