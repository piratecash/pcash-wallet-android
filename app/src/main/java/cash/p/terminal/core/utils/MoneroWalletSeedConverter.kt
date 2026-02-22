package cash.p.terminal.core.utils

import cash.p.terminal.core.toFixedSize
import com.m2049r.xmrwallet.util.ledger.Monero
import io.horizontalsystems.hdwalletkit.Mnemonic
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.crypto.HDPath.parsePath
import java.math.BigInteger

object MoneroWalletSeedConverter {
    private val ed25519CurveOrder =
        BigInteger("1000000000000000000000000000000014DEF9DEA2F79CD65812631A5CF5D3ED", 16)

    fun getLegacySeedFromBip39(
        words: List<String>,
        passphrase: String = "",
        accountIndex: Int = 0
    ): List<String> {
        val seed = Mnemonic().toSeed(words, passphrase)
        val bip32Seed = derivePath(seed, "m/44'/128'/$accountIndex'/0/0")
        val spendKey = reduceECKey(bip32Seed.privKeyBytes.toFixedSize(32))
        return encodePhrase(spendKey)
    }

    private fun derivePath(seed: ByteArray, path: String): DeterministicKey {
        val masterKey = HDKeyDerivation.createMasterPrivateKey(seed)

        val pathParts = parsePath(path.replace("'", "H"))

        var currentKey = masterKey
        for (childNumber in pathParts.list()) {
            currentKey = HDKeyDerivation.deriveChildKey(currentKey, childNumber)
        }

        return currentKey
    }

    private fun encodePhrase(bytes: ByteArray): List<String> {
        require(bytes.size == 32) { "Private key must be exactly 32 bytes" }

        val wordList = Monero.ENGLISH_WORDS
        val wordCount = wordList.size // 1626
        val wordIndices = mutableListOf<Int>()

        for (i in 0 until 32 step 4) {
            val group = ((bytes[i].toUInt() and 0xFFu) shl 0) or
                    ((bytes[i + 1].toUInt() and 0xFFu) shl 8) or
                    ((bytes[i + 2].toUInt() and 0xFFu) shl 16) or
                    ((bytes[i + 3].toUInt() and 0xFFu) shl 24)

            val w1 = (group % wordCount.toUInt()).toInt()
            val w2 = (((group / wordCount.toUInt()) + w1.toUInt()) % wordCount.toUInt()).toInt()
            val w3 =
                (((group / wordCount.toUInt() / wordCount.toUInt()) + w2.toUInt()) % wordCount.toUInt()).toInt()

            wordIndices.add(w1)
            wordIndices.add(w2)
            wordIndices.add(w3)
        }

        val checksumIndex = calculateMoneroChecksum(wordIndices)
        wordIndices.add(checksumIndex)

        return wordIndices.map { wordList[it] }
    }

    private fun calculateMoneroChecksum(wordIndices: List<Int>): Int {
        val wordList = Monero.ENGLISH_WORDS

        var trimmedWords = ""
        wordIndices.forEach { index ->
            val word = wordList[index]
            trimmedWords += word.take(3)
        }

        val crc32 = calculateCRC32(trimmedWords.toByteArray())

        return (crc32 % 24u).toInt().let { checksumIndex ->
            wordIndices[checksumIndex]
        }
    }

    private fun calculateCRC32(data: ByteArray): UInt {
        var crc = 0xFFFFFFFFu

        data.forEach { byte ->
            crc = crc xor (byte.toUInt() and 0xFFu)
            repeat(8) {
                crc = if ((crc and 1u) != 0u) {
                    (crc shr 1) xor 0xEDB88320u
                } else {
                    crc shr 1
                }
            }
        }

        return crc xor 0xFFFFFFFFu
    }

    private fun reduceECKey(buffer: ByteArray): ByteArray {
        // 1. Replicate Dart's _readBytes behavior: read the input buffer as if it were little-endian
        //    Since BigInteger(1, bytes) expects big-endian, we must reverse the bytes first
        //    to effectively treat the original big-endian input as if it were little-endian
        //    for the BigInteger construction.
        val littleEndianBuffer = buffer.reversedArray()
        val num = BigInteger(
            1,
            littleEndianBuffer
        ) // Now, this BigInteger effectively represents the little-endian value

        val reduced = num.mod(ed25519CurveOrder)

        val result = ByteArray(32)
        var temp = reduced
        for (i in 0 until 32) {
            result[i] = temp.and(BigInteger.valueOf(0xff)).toByte()
            temp = temp.shiftRight(8)
        }
        return result
    }
}