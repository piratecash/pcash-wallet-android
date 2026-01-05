package cash.p.terminal.core.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.ILoginRecordRepository
import io.horizontalsystems.core.ISilentPhotoCapture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class SilentCameraManager(
    private val context: Context,
    private val userManager: UserManager
) : ISilentPhotoCapture {

    private fun getPhotosDir(): File {
        return File(context.filesDir, "${ILoginRecordRepository.PHOTOS_DIR}${File.separator}${userManager.getUserLevel()}").apply { mkdirs() }
    }

    override fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun capturePhoto(): Result<String> = suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            var lifecycleOwner: SingleCaptureLifecycleOwner? = null
            var cameraProvider: ProcessCameraProvider? = null

            try {
                cameraProvider = cameraProviderFuture.get()
                lifecycleOwner = SingleCaptureLifecycleOwner()

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

                val photoFile = createPhotoFile()
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            cameraProvider?.unbindAll()
                            lifecycleOwner?.destroy()
                            continuation.resume(Result.success(photoFile.absolutePath))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            cameraProvider?.unbindAll()
                            lifecycleOwner?.destroy()
                            photoFile.delete()
                            continuation.resume(Result.failure(exception))
                        }
                    }
                )
            } catch (e: Exception) {
                cameraProvider?.unbindAll()
                lifecycleOwner?.destroy()
                continuation.resume(Result.failure(e))
            }
        }, ContextCompat.getMainExecutor(context))

        continuation.invokeOnCancellation {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    private fun createPhotoFile(): File {
        val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(Date())
        return File(getPhotosDir(), "IMG_$timestamp.jpg")
    }

    private class SingleCaptureLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.STARTED
        }

        override val lifecycle: Lifecycle get() = registry

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyyMMdd_HHmmss"
    }
}
