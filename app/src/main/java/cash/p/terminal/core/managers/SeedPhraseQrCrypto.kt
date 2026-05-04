package cash.p.terminal.core.managers

import android.util.Base64
import androidx.annotation.VisibleForTesting
import io.horizontalsystems.hdwalletkit.Language
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts seed phrases for QR code sharing.
 * Uses time-based AES-128-CTR encryption with a 3-hour validity window.
 *
 * Encoder always emits JSON v2 plaintext. Decoder dispatches on the first non-whitespace
 * character: '{' => JSON v2, otherwise legacy "words@passphrase|height" format.
 *
 * Legacy parser is conservative — '|height' is only honoured for 25-word seeds, so a
 * BIP39 passphrase ending in '|<digits>' is preserved verbatim instead of being
 * misinterpreted as a Monero restore height.
 */
class SeedPhraseQrCrypto(
    private val timePasswordProvider: TimePasswordProvider
) {

    /**
     * Encrypt seed phrase for QR code (JSON v2 format).
     *
     * @param words Seed phrase words. Must be NFKD-normalized by the caller.
     * @param passphrase BIP39 passphrase (empty string if none). Must be NFKD-normalized.
     * @param height Monero restore height. Required for 25-word Monero seeds, null otherwise.
     * @param language BIP39 wordlist language hint. Optional — improves decoder UX
     *  by avoiding autodetect when the producer already knows the language.
     */
    fun encrypt(
        words: List<String>,
        passphrase: String,
        height: Long? = null,
        language: Language? = null
    ): String {
        val plaintext = buildJsonPlaintext(words, passphrase, height, language)
        return encryptString(plaintext)
    }

    /**
     * Decrypt seed phrase from QR code. Accepts both JSON v2 and legacy plaintext formats.
     */
    fun decrypt(qrContent: String): Result<DecryptedSeed> {
        if (!qrContent.startsWith(QR_PREFIX)) {
            return Result.failure(QrDecodeError.InvalidFormat("Invalid QR format"))
        }

        val encoded = qrContent.removePrefix(QR_PREFIX)
        val combined = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            return Result.failure(QrDecodeError.InvalidFormat("Invalid Base64 encoding"))
        }

        if (combined.size <= IV_SIZE) {
            return Result.failure(QrDecodeError.InvalidFormat("Invalid data length"))
        }

        val iv = combined.copyOfRange(0, IV_SIZE)
        val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)

        val seed = TIME_OFFSETS.firstNotNullOfOrNull { offset ->
            tryDecryptWithOffset(iv, ciphertext, offset)?.let(::parsePlaintext)
        }
        return seed?.let { Result.success(it) }
            ?: Result.failure(QrDecodeError.DecryptFailed("QR decryption failed"))
    }

    /**
     * Distinguishes structural failures (wrong prefix, bad encoding, truncated data)
     * from cryptographic failures (key expired or ciphertext doesn't match this device's
     * time-derived key). UI layer maps each to a distinct user-facing message —
     * "may have expired" is misleading for malformed QRs that never had a chance to be valid.
     */
    sealed class QrDecodeError(message: String) : IllegalArgumentException(message) {
        class InvalidFormat(message: String) : QrDecodeError(message)
        class DecryptFailed(message: String) : QrDecodeError(message)
    }

    private fun tryDecryptWithOffset(
        iv: ByteArray,
        ciphertext: ByteArray,
        offset: Int
    ): String? = try {
        val password = timePasswordProvider.generateTimePassword(offset)
        val key = deriveKey(password)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun parsePlaintext(plaintext: String): DecryptedSeed? {
        val trimmed = plaintext.trimStart()
        return if (trimmed.startsWith(JSON_PREFIX)) {
            parseJsonPlaintext(trimmed)
        } else {
            parseLegacyPlaintext(plaintext)
        }
    }

    private fun encryptString(plaintext: String): String {
        val password = timePasswordProvider.generateTimePassword()
        val key = deriveKey(password)
        val iv = generateRandomIv()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return QR_PREFIX + Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    // ==================== JSON v2 ====================

    private fun buildJsonPlaintext(
        words: List<String>,
        passphrase: String,
        height: Long?,
        language: Language?
    ): String {
        val payload = JsonPayloadV2(
            words = words,
            passphrase = passphrase.takeIf { it.isNotEmpty() },
            height = height,
            language = language?.name
        )
        return jsonCodec.encodeToString(payload)
    }

    private fun parseJsonPlaintext(plaintext: String): DecryptedSeed? {
        val payload = try {
            jsonCodec.decodeFromString<JsonPayloadV2>(plaintext)
        } catch (_: Exception) {
            return null
        }
        if (payload.v != JSON_VERSION) return null
        val seed = DecryptedSeed(
            words = payload.words,
            passphrase = payload.passphrase.orEmpty(),
            height = payload.height,
            language = payload.language?.toLanguageOrNull()
        )
        return seed.takeIf { it.isStructurallyValid() }
    }

    // ==================== Legacy (v1) ====================

    private fun parseLegacyPlaintext(plaintext: String): DecryptedSeed? {
        // Probe word count from the leading words segment; only allow '|height' when the
        // seed has exactly 25 words (Monero) — otherwise '|' inside a passphrase would be
        // silently misinterpreted as a height.
        val probeWords = plaintext.substringBefore(PASSPHRASE_DELIMITER).trim().split(' ')
            .filter { it.isNotBlank() }

        val (withoutHeight, height) = if (probeWords.size == MONERO_WORD_COUNT) {
            extractTrailingHeight(plaintext) ?: (plaintext to null)
        } else {
            plaintext to null
        }

        val parts = withoutHeight.split(PASSPHRASE_DELIMITER, limit = 2)
        val words = parts[0].split(' ').filter { it.isNotBlank() }
        val passphrase = parts.getOrNull(1).orEmpty()

        val seed = DecryptedSeed(words, passphrase, height, language = null)
        return seed.takeIf { it.isStructurallyValid() }
    }

    private fun extractTrailingHeight(plaintext: String): Pair<String, Long>? {
        val idx = plaintext.lastIndexOf(HEIGHT_DELIMITER)
        if (idx < 0) return null
        val height = plaintext.substring(idx + 1).toLongOrNull() ?: return null
        return plaintext.take(idx) to height
    }

    private fun DecryptedSeed.isStructurallyValid(): Boolean {
        if (words.size !in VALID_WORD_COUNTS) return false
        // 25-word seeds are Monero — they must carry a height.
        if (words.size == MONERO_WORD_COUNT && height == null) return false
        return true
    }

    // ==================== Test helpers ====================

    @VisibleForTesting
    internal fun encryptLegacyForTest(
        words: List<String>,
        passphrase: String,
        height: Long?
    ): String {
        val wordsStr = words.joinToString(" ")
        val withPassphrase = if (passphrase.isNotEmpty()) {
            wordsStr + PASSPHRASE_DELIMITER + passphrase
        } else {
            wordsStr
        }
        val full = if (height != null) {
            withPassphrase + HEIGHT_DELIMITER + height
        } else {
            withPassphrase
        }
        return encryptString(full)
    }

    @VisibleForTesting
    internal fun encryptRawForTest(plaintext: String): String = encryptString(plaintext)

    // ==================== Crypto primitives ====================

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
        val height: Long?,        // Non-null for 25-word Monero seeds
        val language: Language?   // Non-null only when the producer included a hint (v2 only)
    )

    @Serializable
    private data class JsonPayloadV2(
        @SerialName("v") val v: Int = JSON_VERSION,
        @SerialName("words") val words: List<String>,
        @SerialName("passphrase") val passphrase: String? = null,
        @SerialName("height") val height: Long? = null,
        @SerialName("language") val language: String? = null
    )

    companion object {
        const val QR_PREFIX = "seed:"

        private const val KEY_SIZE = 16  // AES-128
        private const val IV_SIZE = 16
        private const val TRANSFORMATION = "AES/CTR/NoPadding"
        private const val PASSPHRASE_DELIMITER = "@"
        private const val HEIGHT_DELIMITER = "|"
        private const val JSON_PREFIX = "{"
        private const val JSON_VERSION = 2
        private const val MONERO_WORD_COUNT = 25
        private val VALID_WORD_COUNTS = setOf(12, 15, 18, 21, 24, 25)

        // Try previous, current, and next hour to absorb clock skew.
        private val TIME_OFFSETS = listOf(0, -1, 1)

        private val jsonCodec = Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

        private fun String.toLanguageOrNull(): Language? =
            Language.entries.firstOrNull { it.name == this }
    }
}
