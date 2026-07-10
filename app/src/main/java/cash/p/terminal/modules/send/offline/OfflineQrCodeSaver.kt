package cash.p.terminal.modules.send.offline

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class OfflineQrCodeSaver(
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun save(context: Context, bitmap: Bitmap): Boolean =
        withContext(dispatcherProvider.io) {
            saveBitmap(context, bitmap)
        }

    private fun saveBitmap(context: Context, bitmap: Bitmap): Boolean {
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageValues())
            ?: return false

        val saved = try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, outputStream)
            } ?: false
        } catch (_: Throwable) {
            false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                },
                null,
                null,
            )
        }

        if (!saved) {
            resolver.delete(uri, null, null)
        }

        return saved
    }

    private fun imageValues(): ContentValues =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "pcash-offline-tx-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/P.CASH")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

    private companion object {
        const val PNG_QUALITY = 100
    }
}
