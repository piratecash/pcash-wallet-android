package cash.p.terminal.qr.multipart

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultipartQrFrameTest {

    private val random = Random(7)

    private fun frame(
        seq: Int = 1,
        parts: Int = 1,
        msgLen: Int = MultipartQrEncoder.DEFAULT_FRAGMENT_BYTES,
        bodySize: Int = MultipartQrFrame.bodyLength(seq, msgLen, parts),
        transform: PayloadTransform = PayloadTransform.None,
    ) = MultipartQrFrame(
        transform = transform,
        seq = seq,
        parts = parts,
        msgLen = msgLen,
        msgId = Base32.encode(random.nextBytes(MultipartQrFrame.MSG_ID_BYTES)),
        body = random.nextBytes(bodySize),
    )

    @Test
    fun render_anyFrame_usesOnlyQrAlphanumericCharset() {
        val allowed = ('0'..'9') + ('A'..'Z') + listOf(' ', '$', '%', '*', '+', '-', '.', '/', ':')
        repeat(32) {
            val rendered = frame(msgLen = 1 + random.nextInt(1000)).render()
            val offenders = rendered.filterNot { it in allowed }
            assertTrue(offenders.isEmpty(), "non-alphanumeric chars: $offenders")
        }
    }

    @Test
    fun render_defaultFragment_produces525Chars() {
        assertEquals(525, frame().render().length)
    }

    @Test
    fun render_header_isExactly45Chars() {
        val rendered = frame(bodySize = 0).render()
        assertEquals(MultipartQrFrame.HEADER_CHARS, rendered.length)
    }

    @Test
    fun parse_renderedFrame_returnsEqualFrame() {
        for (parts in listOf(1, 2, 9, 9_999)) {
            val original = frame(seq = parts, parts = parts, msgLen = parts * 3)
            assertEquals(original, MultipartQrFrame.parse(original.render()), "parts $parts")
        }
    }

    @Test
    fun parse_truncatedOrPaddedOrCorrupted_returnsNull() {
        val rendered = frame().render()
        assertNotNull(MultipartQrFrame.parse(rendered))

        assertNull(MultipartQrFrame.parse(rendered.dropLast(1)), "truncated body")
        assertNull(MultipartQrFrame.parse(rendered + "A"), "padded body")
        assertNull(MultipartQrFrame.parse(rendered.replaceAt(rendered.lastIndex, 'a')), "lowercase body char")
        assertNull(MultipartQrFrame.parse(rendered.replaceAt(0, 'Q')), "broken prefix")
        assertNull(MultipartQrFrame.parse(rendered.replaceAt(10, ':')), "wrong separator")
        assertNull(MultipartQrFrame.parse(rendered.replaceAt(6, '+')), "non-digit seq")
        assertNull(MultipartQrFrame.parse(""), "empty")
        assertNull(MultipartQrFrame.parse(MultipartQrFrame.PREFIX), "prefix only")
    }

    @Test
    fun parse_msgLenInconsistentWithBodyLength_returnsNull() {
        // The header promises a 5-byte fragment, the body carries 3.
        val rendered = frame(seq = 1, parts = 2, msgLen = 10, bodySize = 3).render()
        assertNull(MultipartQrFrame.parse(rendered))
    }

    @Test
    fun parse_partsNotMinimal_returnsNull() {
        // 10 bytes across 6 parts implies a 2-byte fragment, which 5 parts already cover.
        val rendered = frame(seq = 1, parts = 6, msgLen = 10, bodySize = 2).render()
        assertNull(MultipartQrFrame.parse(rendered))
    }

    @Test
    fun parse_msgIdWithNonZeroTrailingBits_returnsNull() {
        val rendered = frame().render()
        val lastMsgIdChar = MultipartQrFrame.HEADER_CHARS - 2
        assertNull(MultipartQrFrame.parse(rendered.replaceAt(lastMsgIdChar, 'B')))
    }

    @Test
    fun parse_frameLongerThanMaxQrCapacity_returnsNull() {
        val oversized = MultipartQrEncoder.MAX_FRAGMENT_BYTES + 1
        val rendered = frame(seq = 1, parts = 1, msgLen = oversized).render()
        assertTrue(rendered.length > MultipartQrFrame.MAX_FRAME_CHARS, "length ${rendered.length}")
        assertNull(MultipartQrFrame.parse(rendered))
    }

    @Test
    fun parse_unknownTransformTag_returnsNull() {
        val rendered = frame().render()
        for (tag in listOf('X', 'n', 'h', '0')) {
            assertNull(MultipartQrFrame.parse(rendered.replaceAt(5, tag)), "tag $tag")
        }
    }

    @Test
    fun parse_impliedFragmentAboveMaxFragmentBytes_returnsNull() {
        // Fits MAX_FRAME_CHARS exactly, yet implies a 1485-byte first fragment.
        val rendered = frame(seq = 2, parts = 2, msgLen = 2969, bodySize = 1484).render()
        assertEquals(MultipartQrFrame.MAX_FRAME_CHARS, rendered.length)
        assertNull(MultipartQrFrame.parse(rendered))
    }

    @Test
    fun bodyLength_msgLenDivisibleByParts_lastFragmentIsFullSize() {
        assertEquals(2, MultipartQrFrame.bodyLength(5, 10, 5))
        assertEquals(2, MultipartQrFrame.bodyLength(1, 10, 5))
        // Not divisible: the last fragment is the short one.
        assertEquals(3, MultipartQrFrame.bodyLength(1, 10, 4))
        assertEquals(1, MultipartQrFrame.bodyLength(4, 10, 4))
    }

    private fun String.replaceAt(index: Int, char: Char) =
        substring(0, index) + char + substring(index + 1)
}
