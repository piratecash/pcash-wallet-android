package cash.p.terminal.ui.compose.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The readability cap belongs only to callers that can fall back to the animated tape.
 * Screens without a fallback (the encrypted recovery-phrase QR, the receive address) must keep
 * rendering anything the encoder accepts.
 */
class PcashQrCodePredicatesTest {

    @Test
    fun canEncodeAsPcashQrCode_pastReadabilityLimit_returnsTrue() {
        assertTrue(canEncodeAsPcashQrCode("a".repeat(DENSE_PAYLOAD_SIZE)))
    }

    @Test
    fun canEncodeAsPcashQrCode_pastEncodableLimit_returnsFalse() {
        assertFalse(canEncodeAsPcashQrCode("a".repeat(PcashQrCodeDefaults.MaxEncodableChars + 1)))
    }

    @Test
    fun isReadablePcashQrCode_pastReadabilityLimit_returnsFalse() {
        assertFalse(isReadablePcashQrCode("a".repeat(DENSE_PAYLOAD_SIZE)))
    }

    private companion object {
        /** 121 modules — denser than the cap, well inside what the encoder handles. */
        const val DENSE_PAYLOAD_SIZE = 745
    }
}
