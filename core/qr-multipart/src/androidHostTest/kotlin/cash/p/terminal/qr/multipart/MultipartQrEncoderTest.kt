package cash.p.terminal.qr.multipart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultipartQrEncoderTest {

    private fun headOf(frames: List<String>) = assertNotNull(MultipartQrFrame.parse(frames.first()))

    @Test
    fun encode_emptyText_returnsNull() {
        assertNull(MultipartQrEncoder.encode(""))
    }

    @Test
    fun encode_payloadAboveMaxMsgLen_returnsNull() {
        // Only the rejecting side is asserted: a tape for the largest accepted payload would
        // materialise ~13 MB of frame strings to prove one boundary.
        assertNull(MultipartQrEncoder.encode("z".repeat(MultipartQrFrame.MAX_MSG_LEN + 1)))
    }

    @Test
    fun encode_preferredFragmentOutsideAllowedRange_returnsNull() {
        val text = "zzzz"
        assertNull(MultipartQrEncoder.encode(text, -1))
        assertNull(MultipartQrEncoder.encode(text, 0))
        assertNull(MultipartQrEncoder.encode(text, MultipartQrEncoder.MAX_FRAGMENT_BYTES + 1))
        assertNotNull(MultipartQrEncoder.encode(text, 1))
        assertNotNull(MultipartQrEncoder.encode(text, MultipartQrEncoder.MAX_FRAGMENT_BYTES))
    }

    @Test
    fun encode_malformedUtf16_returnsNullInsteadOfSubstituting() {
        // A lone surrogate has no UTF-8 form. Encoding it leniently would digest '?' and the
        // receiver would accept text the sender never held, so the tape must be refused.
        assertNull(MultipartQrEncoder.encode("\uD800"))
        assertNull(MultipartQrEncoder.encode("prefix\uDC00suffix"))
        assertNotNull(MultipartQrEncoder.encode("😀"), "a well-formed pair still encodes")
    }

    @Test
    fun encode_lowercaseHexText_usesHexTransformAndHalvesPayload() {
        val text = "deadbeef".repeat(100)
        val head = headOf(assertNotNull(MultipartQrEncoder.encode(text)))
        assertEquals(PayloadTransform.Hex, head.transform)
        assertEquals(text.length / 2, head.msgLen)
    }

    @Test
    fun encode_uppercaseHexText_usesNoneTransform() {
        val text = "DEADBEEF"
        val head = headOf(assertNotNull(MultipartQrEncoder.encode(text)))
        assertEquals(PayloadTransform.None, head.transform)
        assertEquals(text.length, head.msgLen)
    }

    @Test
    fun encode_oddLengthOrNonHexText_usesNoneTransform() {
        for (text in listOf("abc", "hello!", "0", "deadbeeg")) {
            val head = headOf(assertNotNull(MultipartQrEncoder.encode(text)))
            assertEquals(PayloadTransform.None, head.transform, "text $text")
        }
    }

    @Test
    fun encode_hexText_roundTripsThroughSessionByteIdentical() {
        val text = "0123456789abcdef".repeat(80)
        val frames = assertNotNull(MultipartQrEncoder.encode(text))
        assertEquals(PayloadTransform.Hex, headOf(frames).transform)
        assertEquals(text, completeOf(frames))
    }

    @Test
    fun encode_payloadAbovePartsBudget_growsFragmentAndStaysWithinMaxParts() {
        val frames = assertNotNull(MultipartQrEncoder.encode("z".repeat(10_000), 1))
        assertEquals(5_000, frames.size)
        assertTrue(frames.size <= MultipartQrFrame.MAX_PARTS)

        val head = headOf(frames)
        assertEquals(2, MultipartQrFrame.fragmentLength(head.msgLen, head.parts), "fragment grew to 2")
        // Minimal: one fewer part would not carry the payload.
        assertTrue((head.parts - 1) * 2 < head.msgLen, "parts are minimal for the fragment")
    }

    @Test
    fun encode_fragmentAtRenderCeiling_producesFrameAtExactlyMaxFrameChars() {
        val size = MultipartQrEncoder.MAX_FRAGMENT_BYTES
        val frames = assertNotNull(MultipartQrEncoder.encode("z".repeat(size), size))
        assertEquals(1, frames.size)
        assertEquals(MultipartQrFrame.MAX_FRAME_CHARS, frames.first().length)
    }

    @Test
    fun encode_everyFrame_fitsMaxFrameChars() {
        val text = "z".repeat(3_000)
        for (fragment in listOf(1, 2, 300, 1_483, 1_484)) {
            val frames = assertNotNull(MultipartQrEncoder.encode(text, fragment))
            val longest = frames.maxOf { it.length }
            assertTrue(longest <= MultipartQrFrame.MAX_FRAME_CHARS, "fragment $fragment gave $longest chars")
        }
    }

    @Test
    fun encode_samePayload_isDeterministic() {
        val text = "the same payload every time"
        assertEquals(MultipartQrEncoder.encode(text, 4), MultipartQrEncoder.encode(text, 4))
    }

    private fun completeOf(frames: List<String>): String {
        val session = MultipartQrScanSession()
        var completed: String? = null
        frames.forEach { frame ->
            val result = session.feed(frame)
            if (result is FeedResult.Complete) completed = result.text
        }
        return assertNotNull(completed, "tape did not complete")
    }
}
