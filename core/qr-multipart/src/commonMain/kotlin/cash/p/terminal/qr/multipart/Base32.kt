package cash.p.terminal.qr.multipart

/**
 * RFC 4648 base32 without padding: '=' is not part of the QR alphanumeric charset.
 *
 * Decoding is canonical — a byte array has exactly one valid encoding, so a string with
 * non-zero trailing bits is rejected rather than silently accepted as a second spelling.
 */
internal object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val MASK = 0x1F

    /** Character counts that no byte array can produce. */
    private val UNREACHABLE_RESIDUES = setOf(1, 3, 6)

    fun encodedLength(byteCount: Int): Int = ceilDiv(byteCount * 8, 5)

    fun encode(bytes: ByteArray): String = buildString(encodedLength(bytes.size)) {
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                append(ALPHABET[(buffer shr bits) and MASK])
            }
        }
        if (bits > 0) {
            append(ALPHABET[(buffer shl (5 - bits)) and MASK])
        }
    }

    /** Returns null on any input this object could not have produced. */
    fun decode(text: String): ByteArray? {
        if (text.length % 8 in UNREACHABLE_RESIDUES) return null

        val out = ByteArray(text.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (char in text) {
            val value = ALPHABET.indexOf(char)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[index++] = ((buffer shr bits) and 0xFF).toByte()
            }
        }
        val trailing = buffer and ((1 shl bits) - 1)
        return if (trailing == 0) out else null
    }
}
