package cash.p.terminal.qr.multipart

// The tag travels in the frame header so the receiver never has to guess how the
// payload was produced: hex text is halved on the wire, everything else is not.
internal enum class PayloadTransform(val tag: Char) {
    None('N') {
        // Strict for the same reason as the decode below, but pointed the other way: a lenient
        // encode substitutes '?' for malformed UTF-16 and the digest is then taken over the
        // substitution, so the receiver would validate text the sender never held.
        override fun apply(text: String): ByteArray? = try {
            text.encodeToByteArray(throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            null
        }

        // Strict on purpose: a forged tape can carry an invalid-UTF-8 payload with a matching
        // digest, and a lenient decode would hand replacement characters out as a good scan.
        override fun inverse(payload: ByteArray): String? = try {
            payload.decodeToString(throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            null
        }
    },
    Hex('H') {
        override fun apply(text: String): ByteArray = text.hexToByteArray()

        override fun inverse(payload: ByteArray): String = payload.toHexString()
    };

    /** Null means the text has no faithful byte representation and must not be sent. */
    abstract fun apply(text: String): ByteArray?

    /** Null means the payload is not representable as text and the tape must be rejected. */
    abstract fun inverse(payload: ByteArray): String?

    companion object {
        fun of(tag: Char): PayloadTransform? = entries.firstOrNull { it.tag == tag }

        /**
         * Hex is chosen only where the inverse is exact: lowercase digits, even length. Uppercase
         * hex goes the [None] way because [toHexString] could not reproduce it.
         */
        fun forText(text: String): PayloadTransform = when {
            text.isEmpty() || text.length % 2 != 0 -> None
            text.all { it in '0'..'9' || it in 'a'..'f' } -> Hex
            else -> None
        }
    }
}

// Ceiling division on non-negative ints. Declared here because the module needs it in three
// places and neither the Kotlin stdlib nor a dependency-free source provides one.
internal fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

internal data class MultipartQrFrame(
    val transform: PayloadTransform,
    val seq: Int,
    val parts: Int,
    val msgLen: Int,
    val msgId: String,
    val body: ByteArray,
) {

    fun render(): String = buildString(HEADER_CHARS + Base32.encodedLength(body.size)) {
        append(PREFIX)
        append(transform.tag)
        append(seq.toString().padStart(SEQ_CHARS, '0'))
        append('-')
        append(parts.toString().padStart(PARTS_CHARS, '0'))
        append(':')
        append(msgLen.toString().padStart(MSG_LEN_CHARS, '0'))
        append(':')
        append(msgId)
        append(':')
        append(Base32.encode(body))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultipartQrFrame) return false
        return transform == other.transform &&
            seq == other.seq &&
            parts == other.parts &&
            msgLen == other.msgLen &&
            msgId == other.msgId &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = transform.hashCode()
        result = 31 * result + seq
        result = 31 * result + parts
        result = 31 * result + msgLen
        result = 31 * result + msgId.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    companion object {
        const val PREFIX = "PQR1:"
        const val MAX_PARTS = 9_999
        const val MAX_MSG_LEN = 9_999_999
        const val MSG_ID_BYTES = 12
        const val MSG_ID_CHARS = 20
        const val HEADER_CHARS = 45

        /** Alphanumeric capacity of a QR at ECC Q — the level animated frames are drawn with. */
        const val MAX_FRAME_CHARS = 2_420

        private const val SEQ_CHARS = 4
        private const val PARTS_CHARS = 4
        private const val MSG_LEN_CHARS = 7

        private const val TRANSFORM_AT = 5
        private const val SEQ_AT = 6
        private const val PARTS_AT = 11
        private const val MSG_LEN_AT = 16
        private const val MSG_ID_AT = 24
        private const val BODY_AT = 45

        // Fixed-width fields need no separator after TRANSFORM; one more character would
        // cost a byte of every fragment.
        private val SEPARATORS = mapOf(10 to '-', 15 to ':', 23 to ':', 44 to ':')

        /** All fragments but the last are exactly this long; the frame carries no length field. */
        fun fragmentLength(msgLen: Int, parts: Int): Int = ceilDiv(msgLen, parts)

        fun bodyLength(seq: Int, msgLen: Int, parts: Int): Int {
            val fragLen = fragmentLength(msgLen, parts)
            return minOf(fragLen, msgLen - (seq - 1) * fragLen)
        }

        /** Returns null on any deviation: everything accepted here we could also have produced. */
        fun parse(text: String): MultipartQrFrame? {
            if (text.length < HEADER_CHARS + 2 || text.length > MAX_FRAME_CHARS) return null
            if (!text.startsWith(PREFIX)) return null
            if (SEPARATORS.any { (at, char) -> text[at] != char }) return null

            val transform = PayloadTransform.of(text[TRANSFORM_AT]) ?: return null
            val seq = text.digitsToInt(SEQ_AT, SEQ_CHARS) ?: return null
            val parts = text.digitsToInt(PARTS_AT, PARTS_CHARS) ?: return null
            val msgLen = text.digitsToInt(MSG_LEN_AT, MSG_LEN_CHARS) ?: return null

            val msgId = text.substring(MSG_ID_AT, MSG_ID_AT + MSG_ID_CHARS)
            if (Base32.decode(msgId)?.size != MSG_ID_BYTES) return null

            val body = Base32.decode(text.substring(BODY_AT)) ?: return null
            if (!isSelfConsistent(seq, parts, msgLen, body.size)) return null

            return MultipartQrFrame(transform, seq, parts, msgLen, msgId, body)
        }

        /**
         * The counters must describe a tape this encoder could have produced: in range, minimal in
         * parts, drawable at ECC Q, and with a body of exactly the implied fragment length.
         */
        private fun isSelfConsistent(seq: Int, parts: Int, msgLen: Int, bodySize: Int): Boolean {
            if (parts !in 1..MAX_PARTS || msgLen !in 1..MAX_MSG_LEN || seq !in 1..parts) return false

            val fragLen = fragmentLength(msgLen, parts)
            return fragLen <= MultipartQrEncoder.MAX_FRAGMENT_BYTES &&
                (parts - 1) * fragLen < msgLen &&
                bodySize == bodyLength(seq, msgLen, parts)
        }

        private fun String.digitsToInt(from: Int, length: Int): Int? {
            var value = 0
            for (index in from until from + length) {
                val digit = this[index]
                if (digit !in '0'..'9') return null
                value = value * 10 + (digit - '0')
            }
            return value
        }
    }
}
