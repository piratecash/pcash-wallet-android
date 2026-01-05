package io.horizontalsystems.core

/**
 * Interface for capturing photos silently (without UI).
 * Used for login logging to capture selfies on login attempts.
 */
interface ISilentPhotoCapture {
    /**
     * Captures a photo using the front camera.
     * @return Result containing the absolute file path to the captured photo, or failure
     */
    suspend fun capturePhoto(): Result<String>

    /**
     * Checks if camera permission is granted.
     * @return true if camera permission is granted, false otherwise
     */
    fun hasCameraPermission(): Boolean
}
