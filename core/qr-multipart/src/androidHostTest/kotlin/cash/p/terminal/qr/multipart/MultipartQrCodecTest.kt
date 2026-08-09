package cash.p.terminal.qr.multipart

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MultipartQrCodecTest {

    /** Single-byte, non-hex characters, so payload size equals text length and the tag stays `N`. */
    private fun text(size: Int, seed: Int = 0): String {
        val random = Random(seed)
        return buildString(size) { repeat(size) { append(('G'..'Z').random(random)) } }
    }

    private fun tape(text: String, fragment: Int = MultipartQrEncoder.DEFAULT_FRAGMENT_BYTES) =
        assertNotNull(MultipartQrEncoder.encode(text, fragment))

    private fun MultipartQrScanSession.feedAll(frames: List<String>): String? {
        var completed: String? = null
        frames.forEach { frame ->
            val result = feed(frame)
            if (result is FeedResult.Complete) completed = result.text
        }
        return completed
    }

    @Test
    fun feed_payloadSizes1AndFragLenAndFragLenPlus1And8Kb_roundTrips() {
        val fragment = MultipartQrEncoder.DEFAULT_FRAGMENT_BYTES
        for (size in listOf(1, fragment, fragment + 1, 8 * 1024)) {
            val original = text(size, seed = size)
            assertEquals(original, MultipartQrScanSession().feedAll(tape(original)), "size $size")
        }
    }

    @Test
    fun feed_msgLenDivisibleByParts_roundTrips() {
        val original = text(600, seed = 3)
        val frames = tape(original)
        assertEquals(2, frames.size)
        assertEquals(original, MultipartQrScanSession().feedAll(frames))
    }

    @Test
    fun feed_maxPartsTape_roundTrips() {
        val original = text(MultipartQrFrame.MAX_PARTS, seed = 4)
        val frames = tape(original, fragment = 1)
        assertEquals(MultipartQrFrame.MAX_PARTS, frames.size)
        assertEquals(original, MultipartQrScanSession().feedAll(frames))
    }

    @Test
    fun feed_randomLossUpTo50PercentOverManyCycles_completesWithOriginalPayload() {
        val original = text(320, seed = 5)
        val frames = tape(original, fragment = 40)
        assertEquals(8, frames.size)

        for (seed in 1..200) {
            val completed = MultipartQrScanSession().feedLossy(frames, Random(seed), cycles = 40)
            assertEquals(original, completed, "seed $seed")
        }
    }

    /** Replays the tape with half the frames dropped and the order shuffled on every cycle. */
    private fun MultipartQrScanSession.feedLossy(
        frames: List<String>,
        random: Random,
        cycles: Int,
    ): String? {
        repeat(cycles) {
            frames.shuffled(random).forEach { frame ->
                if (random.nextInt(2) == 1) {
                    val result = feed(frame)
                    if (result is FeedResult.Complete) return result.text
                }
            }
        }
        return null
    }

    @Test
    fun feed_duplicateFramesForever_doesNotGrowState() {
        val frames = tape(text(600, seed = 6))
        val session = MultipartQrScanSession()
        repeat(10_000) {
            assertEquals(FeedResult.Incomplete(1, 2), session.feed(frames.first()))
        }
    }

    @Test
    fun feed_frameFromAnotherMessage_restartsSessionAndCompletesNewOne() {
        val first = tape(text(600, seed = 7))
        val secondText = text(600, seed = 8)
        val second = tape(secondText)
        val session = MultipartQrScanSession()

        assertEquals(FeedResult.Incomplete(1, 2), session.feed(first.first()))
        assertEquals(FeedResult.Incomplete(1, 2), session.feed(second.first()))
        assertEquals(FeedResult.Complete(secondText), session.feed(second.last()))
    }

    @Test
    fun feed_sameMsgIdDifferentPartsAndMsgLen_restartsWithoutCrashing() {
        val msgId = Base32.encode(ByteArray(MultipartQrFrame.MSG_ID_BYTES))
        val twoParts = handMadeFrame(PayloadTransform.None, seq = 1, parts = 2, msgLen = 2, msgId, byteArrayOf(1))
        val nineParts = handMadeFrame(PayloadTransform.None, seq = 9, parts = 9, msgLen = 9, msgId, byteArrayOf(2))

        val session = MultipartQrScanSession()
        assertEquals(FeedResult.Incomplete(1, 2), session.feed(twoParts))
        // Matching on msgId alone would write slot 8 into an array of 2.
        assertEquals(FeedResult.Incomplete(1, 9), session.feed(nineParts))
    }

    @Test
    fun feed_sameMsgIdDifferentTransform_restartsSession() {
        val msgId = Base32.encode(ByteArray(MultipartQrFrame.MSG_ID_BYTES))
        val asText = handMadeFrame(PayloadTransform.None, seq = 1, parts = 2, msgLen = 2, msgId, byteArrayOf(0x61))
        val asHex = handMadeFrame(PayloadTransform.Hex, seq = 2, parts = 2, msgLen = 2, msgId, byteArrayOf(0x62))

        val session = MultipartQrScanSession()
        assertEquals(FeedResult.Incomplete(1, 2), session.feed(asText))
        // Had the frames been merged, this second one would have completed the assembly.
        assertEquals(FeedResult.Incomplete(1, 2), session.feed(asHex))
    }

    @Test
    fun feed_bodyCorruptedInOneFrame_digestFailsAndNextCycleCompletes() {
        val original = text(900, seed = 9)
        val frames = tape(original)
        assertEquals(3, frames.size)

        val head = assertNotNull(MultipartQrFrame.parse(frames.first()))
        val corruptedBody = head.body.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val corrupted = head.copy(body = corruptedBody).render()
        assertNotNull(MultipartQrFrame.parse(corrupted), "corruption stays valid by format")

        val session = MultipartQrScanSession()
        assertEquals(FeedResult.Incomplete(1, 3), session.feed(corrupted))
        assertEquals(FeedResult.Incomplete(2, 3), session.feed(frames[1]))
        assertEquals(FeedResult.Incomplete(0, 0), session.feed(frames[2]))

        assertEquals(original, session.feedAll(frames))
    }

    @Test
    fun feed_tapeWithInvalidUtf8Payload_returnsIncompleteInsteadOfReplacementChars() {
        // The encoder never produces this, but the sender of a tape is not the encoder.
        val payload = byteArrayOf(0x80.toByte(), 0x81.toByte())
        val msgId = Base32.encode(sha256(payload).copyOf(MultipartQrFrame.MSG_ID_BYTES))
        val frame = handMadeFrame(PayloadTransform.None, seq = 1, parts = 1, msgLen = 2, msgId, payload)

        assertEquals(FeedResult.Incomplete(0, 0), MultipartQrScanSession().feed(frame))
    }

    @Test
    fun feed_prefixedButUnparsableText_returnsNotMultipartVerbatim() {
        for (raw in listOf("PQR1:HELLO", MultipartQrFrame.PREFIX, "PQR1:N0001-0002:0000002:")) {
            assertEquals(FeedResult.NotMultipart(raw), MultipartQrScanSession().feed(raw), raw)
        }
    }

    @Test
    fun feed_nonPrefixedText_returnsNotMultipartVerbatim() {
        for (raw in listOf("", "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", "PQR2:whatever")) {
            assertEquals(FeedResult.NotMultipart(raw), MultipartQrScanSession().feed(raw), raw)
        }
    }

    @Test
    fun feed_completedTape_keepsAcceptingSoALoopingTapeCompletesAgain() {
        val original = text(600, seed = 10)
        val frames = tape(original)
        val session = MultipartQrScanSession()
        assertEquals(original, session.feedAll(frames))
        assertEquals(original, session.feedAll(frames), "session is not terminal")
    }

    private fun handMadeFrame(
        transform: PayloadTransform,
        seq: Int,
        parts: Int,
        msgLen: Int,
        msgId: String,
        body: ByteArray,
    ) = MultipartQrFrame(transform, seq, parts, msgLen, msgId, body).render()
}
