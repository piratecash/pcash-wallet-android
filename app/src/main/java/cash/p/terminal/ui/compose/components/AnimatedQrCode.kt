package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.qr.multipart.MultipartQrEncoder
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay

// The two together set the nominal revolution time; the budget test asserts their product,
// so neither can be raised without someone re-deciding how long a user holds a camera up.
internal const val MAX_ANIMATED_FRAMES = 300
internal const val FRAME_DELAY_MS = 200L

/** Frames for [content], or null when it does not fit the animated budget at all. */
internal fun animatedQrFrames(content: String): List<String>? =
    MultipartQrEncoder.encode(content)?.takeIf { it.size <= MAX_ANIMATED_FRAMES }

@Composable
internal fun AnimatedQrCode(
    frames: List<String>,
    modifier: Modifier = Modifier,
) {
    var frameIndex by remember(frames) { mutableIntStateOf(0) }

    LaunchedEffect(frames) {
        while (true) {
            delay(FRAME_DELAY_MS)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    val content = frames[frameIndex]
    PcashQrCodeImage(
        content = content,
        qrCodePainter = rememberPcashQrCodePainter(content, withLogo = false),
        modifier = modifier,
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun AnimatedQrCodePreview() {
    val frames = MultipartQrEncoder.encode("ab".repeat(1_000)).orEmpty()
    ComposeAppTheme {
        AnimatedQrCode(frames = frames)
    }
}
