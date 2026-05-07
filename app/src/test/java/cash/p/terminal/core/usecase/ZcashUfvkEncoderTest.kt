package cash.p.terminal.core.usecase

import org.junit.Assert.assertTrue
import org.junit.Test

class ZcashUfvkEncoderTest {

    @Test
    fun encode_trezorZcashData_producesValidUfvk() {
        // Real data from Trezor at m/44'/133'/0'
        val chainCode = hexToBytes("fc36be6c953a255f90d1a08014314c4abd2efa0befa63abfd933c3ac70060728")
        val publicKey = hexToBytes("033479b4d22c4fd5367930d3fd321a84bdb3023ebdb0eabd1442d58128d44ce310")

        val ufvk = ZcashUfvkEncoder.encode(chainCode, publicKey)

        assertTrue("UFVK should start with 'uview1'", ufvk.startsWith("uview1"))
        assertTrue("UFVK should be non-trivial length", ufvk.length > 50)
    }

    @Test(expected = IllegalArgumentException::class)
    fun encode_invalidChainCodeSize_throws() {
        ZcashUfvkEncoder.encode(ByteArray(16), ByteArray(33))
    }

    @Test(expected = IllegalArgumentException::class)
    fun encode_invalidPublicKeySize_throws() {
        ZcashUfvkEncoder.encode(ByteArray(32), ByteArray(20))
    }

    @Suppress("SameParameterValue")
    private fun hexToBytes(hex: String): ByteArray {
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
