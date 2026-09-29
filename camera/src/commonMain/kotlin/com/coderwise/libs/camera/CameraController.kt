package com.coderwise.libs.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * A single photo, straight off the sensor. Held as bytes rather than a file path because callers
 * generally forward it somewhere (an upload, a decoder) rather than show the user a file.
 */
class CapturedImage(val bytes: ByteArray, val mimeType: String = "image/jpeg")

/** Which way the sensor faces. A device may have only one; see [CameraController.isSupported]. */
enum class CameraLens { Back, Front }

/**
 * Takes photos, wherever the app happens to be running.
 *
 * This is the seam between shared code and the platform camera: CameraX on Android, AVFoundation on
 * iOS, and nothing at all on targets without one — which is why [isSupported] is part of the
 * contract rather than an assumption callers get to make.
 *
 * Camera *permission* is deliberately not here. Asking for it, explaining a refusal and offering a
 * way back are all the caller's job, and the platforms already have a shared answer for it in
 * `com.coderwise.libs:permissions` — so this stays what it says it is: a sensor. For the same
 * reason the library declares no manifest entries: the consuming app adds
 * `android.permission.CAMERA` (and `NSCameraUsageDescription` on iOS) itself.
 *
 * It lives in the composition rather than in a DI graph because a platform camera needs things only
 * a composition has: a lifecycle to bind the sensor to and a view to draw the preview into.
 */
@Stable
interface CameraController {
    /**
     * False on targets — or devices — with no camera facing the requested [CameraLens], so callers
     * can offer an alternative up front.
     */
    val isSupported: Boolean

    /**
     * Takes a single photo. Fails rather than throws — a missed shot is an ordinary outcome, and so
     * is being asked before [Viewfinder] is on screen, which is what holds the sensor open.
     */
    suspend fun capture(): Result<CapturedImage>

    /**
     * The live feed, and the thing that runs the sensor: it starts when this enters the composition
     * and stops when it leaves. Callers compose it once they have permission — drawing it without
     * is a black rectangle and a platform complaint.
     */
    @Composable
    fun Viewfinder(modifier: Modifier)
}

/**
 * Stands in wherever no platform camera is wired up. It reports itself unsupported so callers show
 * their "no camera" path instead of a dead shutter button.
 */
object UnsupportedCameraController : CameraController {
    override val isSupported: Boolean = false

    override suspend fun capture(): Result<CapturedImage> =
        Result.failure(UnsupportedOperationException("No camera on this platform"))

    /** Nothing to show: callers draw their "no camera" message over this. */
    @Composable
    override fun Viewfinder(modifier: Modifier) = Unit
}

/**
 * The platform camera for this target, scoped to the composition that remembers it — leaving that
 * composition releases the sensor.
 *
 * Android and iOS have real implementations; desktop and web stay unsupported until there is a
 * reason otherwise.
 *
 * Changing [lens] builds a new controller, so the previous one's sensor is released first.
 */
@Composable
expect fun rememberCameraController(lens: CameraLens = CameraLens.Back): CameraController
