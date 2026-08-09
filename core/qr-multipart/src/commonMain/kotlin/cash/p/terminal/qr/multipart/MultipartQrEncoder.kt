package cash.p.terminal.qr.multipart

object MultipartQrEncoder {

    const val DEFAULT_FRAGMENT_BYTES = 300

    /** 1484 bytes render to exactly MAX_FRAME_CHARS; 1485 no longer fit a QR at ECC Q. */
    const val MAX_FRAGMENT_BYTES = 1_484

    /**
     * Splits [text] into a cyclic tape of PQR1 frames.
     *
     * Returns null in exactly four cases, and never throws:
     *  1. [text] is empty;
     *  2. [text] is not faithfully encodable to bytes (malformed UTF-16);
     *  3. the payload derived from [text] exceeds MultipartQrFrame.MAX_MSG_LEN bytes;
     *  4. [preferredFragmentBytes] is outside 1..MAX_FRAGMENT_BYTES.
     *
     * Case 4 is a caller error, not a property of [text]: the same text with a valid
     * fragment size encodes fine. Cases 1-3 mean the text is not representable
     * in the PQR1 format at any fragment size.
     */
    fun encode(text: String, preferredFragmentBytes: Int = DEFAULT_FRAGMENT_BYTES): List<String>? {
        if (text.isEmpty()) return null
        if (preferredFragmentBytes !in 1..MAX_FRAGMENT_BYTES) return null

        val transform = PayloadTransform.forText(text)
        val payload = transform.apply(text) ?: return null
        if (payload.size > MultipartQrFrame.MAX_MSG_LEN) return null

        // Raising the fragment is what keeps the tape within MAX_PARTS, so "too many frames"
        // is not a rejection class: ceilDiv(MAX_MSG_LEN, MAX_PARTS) = 1001 < MAX_FRAGMENT_BYTES.
        val fragmentCap = maxOf(preferredFragmentBytes, ceilDiv(payload.size, MultipartQrFrame.MAX_PARTS))
        val parts = ceilDiv(payload.size, fragmentCap)
        val fragLen = MultipartQrFrame.fragmentLength(payload.size, parts)
        val msgId = Base32.encode(sha256(payload).copyOf(MultipartQrFrame.MSG_ID_BYTES))

        return (1..parts).map { seq ->
            val from = (seq - 1) * fragLen
            val length = MultipartQrFrame.bodyLength(seq, payload.size, parts)
            MultipartQrFrame(
                transform = transform,
                seq = seq,
                parts = parts,
                msgLen = payload.size,
                msgId = msgId,
                body = payload.copyOfRange(from, from + length),
            ).render()
        }
    }
}
