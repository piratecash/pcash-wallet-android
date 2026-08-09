package cash.p.terminal.ui.compose.components

import cash.p.terminal.qr.multipart.MultipartQrEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedQrCodeBudgetTest {

    @Test
    fun budget_maxFramesAtFrameDelay_nominalRevolutionFitsOneMinute() {
        assertTrue(MAX_ANIMATED_FRAMES * FRAME_DELAY_MS <= 60_000)
    }

    @Test
    fun encode_payloadAtFrameBudget_producesExactlyMaxFrames() {
        val frames = requireNotNull(MultipartQrEncoder.encode("g".repeat(90_000)))
        assertEquals(MAX_ANIMATED_FRAMES, frames.size)
    }

    @Test
    fun encode_payloadOneByteOverFrameBudget_exceedsMaxFrames() {
        val frames = requireNotNull(MultipartQrEncoder.encode("g".repeat(90_001)))
        assertTrue(frames.size > MAX_ANIMATED_FRAMES)
    }
}
