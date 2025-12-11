package cash.p.terminal.modules.qrscanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.ALSO_INVERTED to true
            )
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            val width = image.width
            val height = image.height
            val rotation = image.imageInfo.rotationDegrees

            val buffer = image.planes[0].buffer
            val rowStride = image.planes[0].rowStride

            buffer.rewind()
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val sourceData = if (rowStride == width) data else removePadding(data, width, height, rowStride)

            val (luminance, luminanceWidth, luminanceHeight) = when (rotation % 360) {
                90 -> rotateYUV90(sourceData, width, height, width) to Pair(height, width)
                180 -> rotateYUV180(sourceData, width, height, width) to Pair(width, height)
                270 -> rotateYUV270(sourceData, width, height, width) to Pair(height, width)
                else -> sourceData to Pair(width, height)
            }.let { (bytes, size) -> Triple(bytes, size.first, size.second) }

            // Crop center for zoom effect (QR codes appear larger)
            val cropFactor = 0.7
            val cropWidth = (luminanceWidth * cropFactor).toInt()
            val cropHeight = (luminanceHeight * cropFactor).toInt()
            val cropLeft = (luminanceWidth - cropWidth) / 2
            val cropTop = (luminanceHeight - cropHeight) / 2

            val source = PlanarYUVLuminanceSource(
                luminance,
                luminanceWidth,
                luminanceHeight,
                cropLeft,
                cropTop,
                cropWidth,
                cropHeight,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decodeWithState(binaryBitmap)
                result.text?.takeIf { it.isNotEmpty() }?.let { text ->
                    onQrCodeScanned(text)
                }
            } catch (e: NotFoundException) {
                // No QR code found in this frame
            } finally {
                reader.reset()
            }
        } finally {
            image.close()
        }
    }

    private fun removePadding(data: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        if (rowStride == width) return data
        val result = ByteArray(width * height)
        for (y in 0 until height) {
            System.arraycopy(data, y * rowStride, result, y * width, width)
        }
        return result
    }

    private fun rotateYUV90(data: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val rotated = ByteArray(width * height)
        var i = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                rotated[i++] = data[y * rowStride + x]
            }
        }
        return rotated
    }

    private fun rotateYUV180(data: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val rotated = ByteArray(width * height)
        var i = 0
        for (y in height - 1 downTo 0) {
            for (x in width - 1 downTo 0) {
                rotated[i++] = data[y * rowStride + x]
            }
        }
        return rotated
    }

    private fun rotateYUV270(data: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val rotated = ByteArray(width * height)
        var i = 0
        for (x in width - 1 downTo 0) {
            for (y in 0 until height) {
                rotated[i++] = data[y * rowStride + x]
            }
        }
        return rotated
    }
}
