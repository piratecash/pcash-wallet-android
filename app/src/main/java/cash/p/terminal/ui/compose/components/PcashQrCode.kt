package cash.p.terminal.ui.compose.components

import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import cash.p.terminal.R
import cash.p.terminal.core.tryOrNull
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrLogoPadding
import io.github.alexzhirkevich.qrose.options.QrLogoShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlin.math.roundToInt

internal object PcashQrCodeDefaults {
    val Size = 300.dp
    val QuietZone = 8.dp
    const val QuietZoneModules = 4f
    const val SavedBitmapSize = 1024
}

@Composable
internal fun rememberPcashQrCodePainter(content: String): Painter {
    val logoPainter = adaptiveIconPainterResource(
        id = R.mipmap.launcher_main,
        fallbackDrawable = R.drawable.launcher_main_preview
    )

    return rememberQrCodePainter(content) {
        errorCorrectionLevel = QrErrorCorrectionLevel.MediumHigh
        logo {
            painter = logoPainter
            padding = QrLogoPadding.Natural(.25f)
            shape = QrLogoShape.roundCorners(0.8f)
            size = 0.2f
        }

        shapes {
            ball = QrBallShape.roundCorners(.25f)
            darkPixel = QrPixelShape.roundCorners()
            frame = QrFrameShape.roundCorners(.25f)
        }
    }
}

@Composable
internal fun PcashQrCodeImage(
    content: String,
    qrCodePainter: Painter,
    modifier: Modifier = Modifier,
    qrCodeSize: Dp = PcashQrCodeDefaults.Size,
    contentScale: ContentScale = ContentScale.FillWidth,
) {
    val quietZone = remember(content, qrCodeSize) {
        calculatePcashQrQuietZone(content, qrCodeSize)
    }
    Image(
        painter = qrCodePainter,
        modifier = modifier
            .padding(quietZone)
            .fillMaxSize(),
        contentScale = contentScale,
        contentDescription = null,
    )
}

@Composable
internal fun PcashQrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    qrCodeSize: Dp = PcashQrCodeDefaults.Size,
    contentScale: ContentScale = ContentScale.FillWidth,
) {
    PcashQrCodeImage(
        content = content,
        qrCodePainter = rememberPcashQrCodePainter(content),
        modifier = modifier,
        qrCodeSize = qrCodeSize,
        contentScale = contentScale,
    )
}

private fun calculatePcashQrQuietZone(content: String, qrCodeSize: Dp): Dp {
    val modules = qrModuleCount(content) ?: return PcashQrCodeDefaults.QuietZone
    val moduleSize = qrCodeSize.value / modules
    return (moduleSize * PcashQrCodeDefaults.QuietZoneModules).dp
        .coerceAtLeast(PcashQrCodeDefaults.QuietZone)
}

internal fun createPcashQrCodeBitmap(
    content: String,
    painter: Painter,
    density: Density,
): Bitmap {
    val quietZone = calculatePcashQrQuietZonePx(content, density)
    val bitmapSize = PcashQrCodeDefaults.SavedBitmapSize + quietZone * 2
    val bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap.asImageBitmap())
    val drawScope = CanvasDrawScope()

    drawScope.draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(bitmapSize.toFloat(), bitmapSize.toFloat()),
    ) {
        drawRect(Color.White)
        translate(left = quietZone.toFloat(), top = quietZone.toFloat()) {
            with(painter) {
                draw(
                    size = Size(
                        PcashQrCodeDefaults.SavedBitmapSize.toFloat(),
                        PcashQrCodeDefaults.SavedBitmapSize.toFloat()
                    )
                )
            }
        }
    }

    return bitmap
}

@Composable
private fun adaptiveIconPainterResource(
    @DrawableRes id: Int,
    @DrawableRes fallbackDrawable: Int,
): Painter {
    val context = LocalContext.current
    return remember(id, fallbackDrawable) {
        val adaptiveIcon = ResourcesCompat.getDrawable(
            context.resources,
            id,
            context.theme
        ) as? AdaptiveIconDrawable
        adaptiveIcon?.let { BitmapPainter(it.toBitmap().asImageBitmap()) }
    } ?: painterResource(fallbackDrawable)
}

private fun calculatePcashQrQuietZonePx(content: String, density: Density): Int {
    val defaultQuietZone = with(density) { PcashQrCodeDefaults.QuietZone.toPx() }
    val modules = qrModuleCount(content) ?: return defaultQuietZone.roundToInt()
    val moduleSize = PcashQrCodeDefaults.SavedBitmapSize.toFloat() / modules
    return (moduleSize * PcashQrCodeDefaults.QuietZoneModules)
        .coerceAtLeast(defaultQuietZone)
        .roundToInt()
}

private fun qrModuleCount(content: String): Int? =
    tryOrNull {
        Encoder.encode(content, ErrorCorrectionLevel.Q).matrix?.width?.takeIf { it > 0 }
    }
