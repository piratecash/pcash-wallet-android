package cash.p.terminal.core.usecase

import io.horizontalsystems.bitcoincore.crypto.Bech32
import io.horizontalsystems.bitcoincore.crypto.Bech32Segwit
import org.bouncycastle.crypto.digests.Blake2bDigest
import kotlin.math.min

/**
 * Encodes a transparent-only Unified Full Viewing Key (UFVK) per ZIP 316 Revision 1.
 *
 * Trezor provides a BIP44 xpub at m/44'/133'/0' which yields chainCode (32 bytes)
 * and compressed publicKey (33 bytes). These form the transparent P2PKH FVK item
 * (typecode 0x00, 65 bytes). A Revision 1 UFVK allows transparent-only keys.
 *
 * @see <a href="https://zips.z.cash/zip-0316">ZIP 316</a>
 */
object ZcashUfvkEncoder {

    private const val HRP_MAINNET = "uview"
    private const val TYPECODE_P2PKH = 0x00
    private const val TYPECODE_METADATA_REVISION = 0xF0
    private const val REVISION = 1
    private const val BLAKE2B_OUTPUT = 64

    fun encode(chainCode: ByteArray, publicKey: ByteArray): String {
        require(chainCode.size == 32) { "chainCode must be 32 bytes" }
        require(publicKey.size == 33) { "publicKey must be 33 bytes" }

        val transparentItem = compactItem(TYPECODE_P2PKH, chainCode + publicKey)
        val revisionItem = compactItem(TYPECODE_METADATA_REVISION, byteArrayOf(REVISION.toByte()))

        // ZIP 316: non-metadata items in ascending typecode order, then metadata items
        val rawEncoding = transparentItem + revisionItem

        val paddedHrp = ByteArray(16)
        HRP_MAINNET.toByteArray(Charsets.US_ASCII).copyInto(paddedHrp)

        val jumbled = f4Jumble(rawEncoding + paddedHrp)

        val data5bit = Bech32Segwit.convertBits(jumbled, 0, jumbled.size, 8, 5, true)
        return Bech32Segwit.encode(HRP_MAINNET, Bech32.Encoding.BECH32M, data5bit)
    }

    private fun compactItem(typecode: Int, data: ByteArray): ByteArray {
        return compactSize(typecode) + compactSize(data.size) + data
    }

    private fun compactSize(value: Int): ByteArray = when {
        value < 0xFD -> byteArrayOf(value.toByte())
        value <= 0xFFFF -> byteArrayOf(0xFD.toByte(), (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
        else -> error("compactSize value too large: $value")
    }

    // --- F4Jumble per ZIP 316 ---

    private fun f4Jumble(message: ByteArray): ByteArray {
        val lM = message.size
        require(lM >= 38) { "F4Jumble input must be >= 38 bytes, got $lM" }

        val lL = min(BLAKE2B_OUTPUT, lM / 2)
        val lR = lM - lL

        val a = message.copyOfRange(0, lL)
        val b = message.copyOfRange(lL, lM)

        val x = xor(b, g(0, a, lR))
        val y = xor(a, h(0, x, lL))
        val d = xor(x, g(1, y, lR))
        val c = xor(y, h(1, d, lL))

        return c + d
    }

    private fun h(i: Int, u: ByteArray, outputLen: Int): ByteArray {
        val personalization = "UA_F4Jumble_H".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(i.toByte(), 0, 0)
        val digest = Blake2bDigest(null, outputLen, null, personalization)
        digest.update(u, 0, u.size)
        val out = ByteArray(outputLen)
        digest.doFinal(out, 0)
        return out
    }

    private fun g(i: Int, u: ByteArray, targetLen: Int): ByteArray {
        val blocks = (targetLen + BLAKE2B_OUTPUT - 1) / BLAKE2B_OUTPUT
        val result = ByteArray(blocks * BLAKE2B_OUTPUT)
        for (j in 0 until blocks) {
            val personalization = "UA_F4Jumble_G".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(i.toByte()) +
                byteArrayOf((j and 0xFF).toByte(), ((j shr 8) and 0xFF).toByte())
            val digest = Blake2bDigest(null, BLAKE2B_OUTPUT, null, personalization)
            digest.update(u, 0, u.size)
            digest.doFinal(result, j * BLAKE2B_OUTPUT)
        }
        return result.copyOf(targetLen)
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        return ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }
}
