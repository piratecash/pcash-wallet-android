package cash.p.terminal.core.managers

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts seed phrases for QR code sharing.
 * Uses time-based AES-128-CTR encryption with a 3-hour validity window.
 *
 * @param timePasswordProvider Provider for time-based passwords (injectable for testing)
 */
class SeedPhraseQrCrypto(
    private val timePasswordProvider: TimePasswordProvider
) {

    /**
     * Encrypt seed phrase for QR code.
     * @param words Seed phrase words (12-25 words)
     * @param passphrase BIP39 passphrase (empty string if none)
     * @param height Monero restore height (null for non-Monero, required for 25-word Monero seeds)
     */
    fun encrypt(words: List<String>, passphrase: String, height: Long? = null): String {
        val plaintext = buildPlaintext(words, passphrase, height)
        val password = timePasswordProvider.generateTimePassword()
        val key = deriveKey(password)
        val iv = generateRandomIv()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = iv + ciphertext
        return QR_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt seed phrase from QR code.
     * @return Triple of (words, passphrase, height) where height is non-null for Monero 25-word seeds
     */
    fun decrypt(qrContent: String): Result<DecryptedSeed> {
        if (!qrContent.startsWith(QR_PREFIX)) {
            return Result.failure(IllegalArgumentException("Invalid QR format"))
        }

        val encoded = qrContent.removePrefix(QR_PREFIX)
        val combined = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Invalid Base64 encoding"))
        }

        if (combined.size <= IV_SIZE) {
            return Result.failure(IllegalArgumentException("Invalid data length"))
        }

        val iv = combined.copyOfRange(0, IV_SIZE)
        val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)

        // Try decryption with previous, current, and next hour
        for (offset in listOf(0, -1, 1)) {
            val password = timePasswordProvider.generateTimePassword(offset)
            val key = deriveKey(password)

            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                val plaintext = String(cipher.doFinal(ciphertext), Charsets.UTF_8)

                val decrypted = parsePlaintext(plaintext)
                if (decrypted != null) {
                    return Result.success(decrypted)
                }
            } catch (_: Exception) {
                // Try next offset
            }
        }

        return Result.failure(IllegalArgumentException("Decryption failed - QR may have expired"))
    }

    /**
     * Build plaintext for encryption.
     * Format: "words@passphrase|height" where @passphrase and |height are optional
     */
    private fun buildPlaintext(words: List<String>, passphrase: String, height: Long?): String {
        val wordsStr = words.joinToString(" ")
        val withPassphrase = if (passphrase.isNotEmpty()) {
            wordsStr + PASSPHRASE_DELIMITER + passphrase
        } else {
            wordsStr
        }
        return if (height != null) {
            withPassphrase + HEIGHT_DELIMITER + height
        } else {
            withPassphrase
        }
    }

    /**
     * Parse decrypted plaintext.
     * Format: "words@passphrase|height" where @passphrase and |height are optional
     * Height delimiter is parsed from the END to allow | in passphrase
     */
    private fun parsePlaintext(plaintext: String): DecryptedSeed? {
        // First extract height if present (from last occurrence of delimiter)
        val lastHeightDelimiter = plaintext.lastIndexOf(HEIGHT_DELIMITER)
        val (withoutHeight, height) = if (lastHeightDelimiter >= 0) {
            val potentialHeight = plaintext.substring(lastHeightDelimiter + 1).toLongOrNull()
            if (potentialHeight != null) {
                // Valid height found
                plaintext.take(lastHeightDelimiter) to potentialHeight
            } else {
                // Not a valid height, treat entire string as content
                plaintext to null
            }
        } else {
            plaintext to null
        }

        // Then extract passphrase (from first occurrence of @)
        val parts = withoutHeight.split(PASSPHRASE_DELIMITER, limit = 2)
        val wordsStr = parts[0]
        val passphrase = if (parts.size > 1) parts[1] else ""

        val words = wordsStr.split(" ").filter { it.isNotBlank() }

        // Validate: should be 12, 15, 18, 21, 24, or 25 words (25 for Monero)
        if (words.size !in listOf(12, 15, 18, 21, 24, 25)) {
            return null
        }

        // Basic validation: all words should be lowercase letters only
        if (words.any { word -> !word.all { it.isLowerCase() } }) {
            return null
        }

        // 25 words must have height (Monero)
        if (words.size == 25 && height == null) {
            return null
        }

        return DecryptedSeed(words, passphrase, height)
    }

    private fun deriveKey(password: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hash.copyOfRange(0, KEY_SIZE)
    }

    private fun generateRandomIv(): ByteArray {
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        return iv
    }

    data class DecryptedSeed(
        val words: List<String>,
        val passphrase: String,
        val height: Long?  // Non-null for 25-word Monero seeds
    )

    companion object {
        const val QR_PREFIX = "seed:"
        private const val KEY_SIZE = 16  // AES-128
        private const val IV_SIZE = 16
        private const val TRANSFORMATION = "AES/CTR/NoPadding"
        private const val PASSPHRASE_DELIMITER = "@"
        private const val HEIGHT_DELIMITER = "|"
    }
}
