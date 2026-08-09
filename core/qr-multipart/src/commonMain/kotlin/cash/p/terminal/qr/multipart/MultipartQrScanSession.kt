package cash.p.terminal.qr.multipart

sealed interface FeedResult {
    /** The scanned text is not part of a tape and must be handled the way single QR codes are. */
    data class NotMultipart(val raw: String) : FeedResult

    data class Complete(val text: String) : FeedResult

    data class Incomplete(val received: Int, val total: Int) : FeedResult
}

/**
 * Reassembles a PQR1 tape from frames arriving in any order, with duplicates and gaps.
 *
 * The session is not terminal: after [FeedResult.Complete] it keeps accepting frames, because the
 * tape keeps looping and the caller — not the codec — decides when scanning stops.
 */
class MultipartQrScanSession {

    private var assembly: Assembly? = null

    fun feed(frame: String): FeedResult {
        // A string that merely starts with the prefix but does not parse is ordinary text: it is
        // scannable today and must stay scannable.
        val parsed = MultipartQrFrame.parse(frame) ?: return FeedResult.NotMultipart(frame)

        val current = assembly?.takeIf { it.matches(parsed) } ?: Assembly(parsed).also { assembly = it }
        current.accept(parsed)
        if (current.received < current.parts) {
            return FeedResult.Incomplete(current.received, current.parts)
        }

        assembly = null
        val text = current.assembled()
        return if (text == null) FeedResult.Incomplete(0, 0) else FeedResult.Complete(text)
    }

    /**
     * Frames belong together only when the whole quadruple matches. Matching on msgId alone would
     * let a frame with a larger seq write past the slot array, and the camera feed is untrusted.
     */
    private class Assembly(first: MultipartQrFrame) {
        val parts = first.parts

        private val transform = first.transform
        private val msgLen = first.msgLen
        private val msgId = first.msgId
        private val slots = arrayOfNulls<ByteArray>(parts)

        var received = 0
            private set

        fun matches(frame: MultipartQrFrame): Boolean =
            frame.transform == transform &&
                frame.parts == parts &&
                frame.msgLen == msgLen &&
                frame.msgId == msgId

        fun accept(frame: MultipartQrFrame) {
            if (slots[frame.seq - 1] != null) return
            slots[frame.seq - 1] = frame.body
            received++
        }

        /** Null when the assembled payload is unusable — a digest mismatch or undecodable text. */
        fun assembled(): String? {
            val payload = ByteArray(msgLen)
            var at = 0
            for (slot in slots) {
                val fragment = checkNotNull(slot)
                fragment.copyInto(payload, at)
                at += fragment.size
            }
            val digest = Base32.encode(sha256(payload).copyOf(MultipartQrFrame.MSG_ID_BYTES))
            if (digest != msgId) return null
            // Inverted only after the digest holds: unwrapping bytes already known to be wrong
            // buys nothing.
            return transform.inverse(payload)
        }
    }
}
