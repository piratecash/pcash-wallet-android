package cash.p.terminal.core.managers

import org.bouncycastle.crypto.generators.SCrypt
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Deniable Encryption Manager for V4 Backup Format
 *
 * Provides plausible deniability through:
 * - Password-derived offsets within a random noise container
 * - AES-256-GCM authenticated encryption
 * - Random padding to prevent known-plaintext attacks
 * - Each password decrypts only its own data
 * - Dynamic container sizing with size buckets for deniability
 *
 * Container structure:
 * [32B salt][...data region with embedded encrypted blocks...]
 *
 * Each encrypted block:
 * [4B obfuscated_length][12B nonce][ciphertext][16B GCM tag]
 *
 * Inside ciphertext (decrypted):
 * [1B padding_len][0-32B random_padding][4B payload_len][payload bytes]
 *
 * Dynamic sizing:
 * - Container size adapts to payload size (min 50KB, max 10MB)
 * - Offset calculation uses fixed modulo (independent of container size)
 * - Size is rounded to buckets (50KB, 100KB, 200KB, 500KB, 1MB, 5MB, 10MB)
 *   to prevent exact payload size leakage
 */
object DeniableEncryptionManager {

    // ========== Constants ==========
    private const val MIN_CONTAINER_SIZE = 50_000       // 50KB minimum
    private const val MAX_CONTAINER_SIZE = 10_000_000  // 10MB maximum
    private const val OFFSET_MODULO = 100_000          // Fixed offset range (~100KB max offset)

    /**
     * Recommended max retries for collision handling.
     * With ~70% collision probability for large payloads, 50 retries gives P(failure) ≈ 10^-10.
     */
    const val RECOMMENDED_MAX_RETRIES = 50

    // Size buckets for deniability - container rounds up to nearest bucket
    // This prevents container size from revealing exact payload size
    // Note: All buckets must be >= MIN_CONTAINER_SIZE
    private val SIZE_BUCKETS = listOf(
        50_000,      // 50KB (minimum)
        100_000,     // 100KB
        200_000,     // 200KB
        500_000,     // 500KB
        1_000_000,   // 1MB
        5_000_000,   // 5MB
        10_000_000   // 10MB
    )

    private const val SALT_LENGTH = 32
    private const val HEADER_SIZE = SALT_LENGTH  // salt only = 32 bytes

    private const val GCM_NONCE_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val LENGTH_FIELD_SIZE = 4
    private const val RANDOM_PADDING_MAX = 32

    // Scrypt parameters (consistent with V3)
    private const val SCRYPT_N = 16384
    private const val SCRYPT_R = 8
    private const val SCRYPT_P = 4
    private const val SCRYPT_DKLEN = 32

    private val secureRandom = SecureRandom()

    // ========== Data Classes ==========

    data class MessageEntry(
        val payload: ByteArray,
        val password: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MessageEntry) return false
            return payload.contentEquals(other.payload) && password == other.password
        }

        override fun hashCode(): Int = 31 * payload.contentHashCode() + password.hashCode()
    }

    class PasswordCollisionException(message: String) : IllegalArgumentException(message)

    // ========== Key Derivation ==========

    /**
     * Derives master key using scrypt KDF with random salt
     */
    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        return SCrypt.generate(passwordBytes, salt, SCRYPT_N, SCRYPT_R, SCRYPT_P, SCRYPT_DKLEN)
    }

    /**
     * HKDF-Expand using HMAC-SHA256 (RFC 5869)
     */
    private fun hkdfExpand(prk: ByteArray, info: String, length: Int): ByteArray {
        val infoBytes = info.toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter: Byte = 1

        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(infoBytes)
            mac.update(counter)
            t = mac.doFinal()

            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
            counter++
        }
        return result
    }

    /** Derive AES encryption key from master key */
    private fun deriveEncKey(masterKey: ByteArray): ByteArray = hkdfExpand(masterKey, "enc", 32)

    /** Derive offset calculation key from master key */
    private fun deriveOffsetKey(masterKey: ByteArray): ByteArray = hkdfExpand(masterKey, "offset", 32)

    /** Derive length obfuscation key from master key */
    private fun deriveLengthKey(masterKey: ByteArray): ByteArray = hkdfExpand(masterKey, "len", 4)

    // ========== Offset Calculation ==========

    /**
     * Compute deterministic offset from offset key using 64-bit entropy.
     * Uses fixed OFFSET_MODULO to ensure offset is independent of container size.
     */
    private fun computeOffset(offsetKey: ByteArray): Int {
        val value = ByteBuffer.wrap(offsetKey, 0, 8).long
        return Math.floorMod(value, OFFSET_MODULO.toLong()).toInt()
    }

    /**
     * Round size up to nearest bucket for deniability.
     * Prevents container size from revealing exact payload size.
     * Ensures result is at least MIN_CONTAINER_SIZE.
     */
    private fun roundToSizeBucket(size: Int): Int {
        val minSize = maxOf(size, MIN_CONTAINER_SIZE)
        return SIZE_BUCKETS.firstOrNull { it >= minSize } ?: MAX_CONTAINER_SIZE
    }

    // ========== AES-GCM Encryption ==========

    /**
     * AES-256-GCM encryption with random nonce
     * Output: [12-byte nonce][ciphertext][16-byte auth tag]
     */
    private fun aesGcmEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val nonce = ByteArray(GCM_NONCE_LENGTH)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))

        val ciphertext = cipher.doFinal(plaintext)

        return ByteArray(GCM_NONCE_LENGTH + ciphertext.size).also {
            System.arraycopy(nonce, 0, it, 0, GCM_NONCE_LENGTH)
            System.arraycopy(ciphertext, 0, it, GCM_NONCE_LENGTH, ciphertext.size)
        }
    }

    /**
     * AES-256-GCM decryption
     * Returns null if auth tag verification fails
     */
    private fun aesGcmDecrypt(data: ByteArray, key: ByteArray): ByteArray? {
        if (data.size < GCM_NONCE_LENGTH + 16) return null

        return try {
            val nonce = data.copyOfRange(0, GCM_NONCE_LENGTH)
            val ciphertext = data.copyOfRange(GCM_NONCE_LENGTH, data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }

    // ========== Block Creation ==========

    /**
     * Create encrypted block with random padding
     *
     * Block format: [4-byte obfuscated length][encrypted data]
     * Encrypted data: [nonce][ciphertext of: [1-byte pad len][padding][4-byte payload len][payload]]
     */
    private fun createEncryptedBlock(payload: ByteArray, masterKey: ByteArray): ByteArray {
        val encKey = deriveEncKey(masterKey)
        val lengthKey = deriveLengthKey(masterKey)

        // Random padding (0-32 bytes) for known-plaintext protection
        val padLen = secureRandom.nextInt(RANDOM_PADDING_MAX + 1)
        val padding = ByteArray(padLen).also { secureRandom.nextBytes(it) }

        // Plaintext: [pad_len:1][padding:N][payload_len:4][payload:M]
        val plaintext = ByteArray(1 + padLen + 4 + payload.size)
        var pos = 0
        plaintext[pos++] = padLen.toByte()
        System.arraycopy(padding, 0, plaintext, pos, padLen)
        pos += padLen
        plaintext[pos++] = ((payload.size shr 24) and 0xFF).toByte()
        plaintext[pos++] = ((payload.size shr 16) and 0xFF).toByte()
        plaintext[pos++] = ((payload.size shr 8) and 0xFF).toByte()
        plaintext[pos++] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, plaintext, pos, payload.size)

        val encrypted = aesGcmEncrypt(plaintext, encKey)

        // Obfuscate length with password-derived key
        val block = ByteArray(LENGTH_FIELD_SIZE + encrypted.size)
        block[0] = (((encrypted.size shr 24) and 0xFF) xor (lengthKey[0].toInt() and 0xFF)).toByte()
        block[1] = (((encrypted.size shr 16) and 0xFF) xor (lengthKey[1].toInt() and 0xFF)).toByte()
        block[2] = (((encrypted.size shr 8) and 0xFF) xor (lengthKey[2].toInt() and 0xFF)).toByte()
        block[3] = ((encrypted.size and 0xFF) xor (lengthKey[3].toInt() and 0xFF)).toByte()
        System.arraycopy(encrypted, 0, block, LENGTH_FIELD_SIZE, encrypted.size)

        return block
    }

    // ========== Container Operations ==========

    /**
     * Generate random salt for container
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Create deniable encryption container with one or two messages.
     * Automatically retries with new salt if password collision occurs.
     *
     * @param message1 Primary message (required)
     * @param password1 Primary password (required)
     * @param message2 Secondary/hidden message (optional)
     * @param password2 Secondary password (optional, required if message2 provided)
     * @param maxRetries Maximum retry attempts for collision resolution
     * @return Container as raw ByteArray
     * @throws PasswordCollisionException if collision persists after maxRetries
     * @throws IllegalArgumentException if data exceeds maximum container size
     */
    @Throws(PasswordCollisionException::class)
    fun createContainerBytes(
        message1: ByteArray,
        password1: String,
        message2: ByteArray?,
        password2: String?,
        maxRetries: Int = RECOMMENDED_MAX_RETRIES
    ): ByteArray {
        repeat(maxRetries) {
            val salt = generateSalt()
            try {
                return createContainerBytes(message1, password1, message2, password2, salt)
            } catch (e: PasswordCollisionException) {
                // Retry with new salt
            }
        }
        throw PasswordCollisionException("Failed to create container after $maxRetries attempts due to password collisions")
    }

    /**
     * Create deniable encryption container with one or two messages using provided salt.
     * Container size is dynamically calculated based on data size and rounded
     * to size buckets for deniability.
     *
     * @param message1 Primary message (required)
     * @param password1 Primary password (required)
     * @param message2 Secondary/hidden message (optional)
     * @param password2 Secondary password (optional, required if message2 provided)
     * @param salt Random salt for KDF (will be stored in header)
     * @return Container as raw ByteArray
     * @throws PasswordCollisionException if passwords derive overlapping offsets
     * @throws IllegalArgumentException if data exceeds maximum container size
     */
    @Throws(PasswordCollisionException::class)
    fun createContainerBytes(
        message1: ByteArray,
        password1: String,
        message2: ByteArray?,
        password2: String?,
        salt: ByteArray
    ): ByteArray {
        require(salt.size == SALT_LENGTH) { "Salt must be $SALT_LENGTH bytes" }

        // Derive master keys
        val masterKey1 = deriveKey(password1, salt)
        val offsetKey1 = deriveOffsetKey(masterKey1)

        // Create encrypted block 1
        val block1 = createEncryptedBlock(message1, masterKey1)
        val offset1 = computeOffset(offsetKey1)

        // Calculate required size for block1
        var requiredSize = HEADER_SIZE + offset1 + block1.size

        val range1 = offset1 until (offset1 + block1.size)

        // Handle optional second message
        var block2: ByteArray? = null
        var offset2: Int? = null

        if (message2 != null && password2 != null) {
            val masterKey2 = deriveKey(password2, salt)
            val offsetKey2 = deriveOffsetKey(masterKey2)

            block2 = createEncryptedBlock(message2, masterKey2)
            offset2 = computeOffset(offsetKey2)

            // Update required size to accommodate both blocks
            val requiredSize2 = HEADER_SIZE + offset2 + block2.size
            requiredSize = maxOf(requiredSize, requiredSize2)

            val range2 = offset2 until (offset2 + block2.size)

            // Check for collision
            if (rangesOverlap(range1, range2)) {
                throw PasswordCollisionException("Passwords conflict - they derive overlapping positions. Please choose a different password.")
            }
        }

        // Check maximum size limit
        if (requiredSize > MAX_CONTAINER_SIZE) {
            throw IllegalArgumentException("Data is too large: required $requiredSize bytes, maximum is $MAX_CONTAINER_SIZE bytes")
        }

        // Round to size bucket for deniability
        val containerSize = roundToSizeBucket(requiredSize)

        // Create container filled with random data
        val container = ByteArray(containerSize)
        secureRandom.nextBytes(container)

        // Write salt at the beginning
        System.arraycopy(salt, 0, container, 0, SALT_LENGTH)

        // Place block1 at offset1
        val absOffset1 = HEADER_SIZE + offset1
        System.arraycopy(block1, 0, container, absOffset1, block1.size)

        // Place block2 at offset2 if exists
        if (block2 != null && offset2 != null) {
            val absOffset2 = HEADER_SIZE + offset2
            System.arraycopy(block2, 0, container, absOffset2, block2.size)
        }

        return container
    }

    /**
     * Check if two ranges overlap
     */
    private fun rangesOverlap(r1: IntRange, r2: IntRange): Boolean {
        return r1.first <= r2.last && r2.first <= r1.last
    }

    /**
     * Extract message from container bytes using password.
     * Accepts variable size containers (from MIN_CONTAINER_SIZE to MAX_CONTAINER_SIZE).
     *
     * @param container Raw container bytes
     * @param password Password to decrypt with
     * @return Decrypted payload bytes, or null if wrong password
     */
    fun extractMessageFromBytes(container: ByteArray, password: String): ByteArray? {
        // Accept any valid container size
        if (container.size < MIN_CONTAINER_SIZE || container.size > MAX_CONTAINER_SIZE) {
            return null
        }

        // Extract salt from the beginning
        val salt = container.copyOfRange(0, SALT_LENGTH)

        // Derive keys
        val masterKey = deriveKey(password, salt)
        val encKey = deriveEncKey(masterKey)
        val offsetKey = deriveOffsetKey(masterKey)
        val lengthKey = deriveLengthKey(masterKey)

        // Compute offset using fixed modulo (independent of container size)
        val offset = computeOffset(offsetKey)
        val absOffset = HEADER_SIZE + offset

        if (absOffset + LENGTH_FIELD_SIZE > container.size) {
            return null
        }

        // Read obfuscated length
        val encLen = (((container[absOffset].toInt() and 0xFF) xor (lengthKey[0].toInt() and 0xFF)) shl 24) or
                (((container[absOffset + 1].toInt() and 0xFF) xor (lengthKey[1].toInt() and 0xFF)) shl 16) or
                (((container[absOffset + 2].toInt() and 0xFF) xor (lengthKey[2].toInt() and 0xFF)) shl 8) or
                ((container[absOffset + 3].toInt() and 0xFF) xor (lengthKey[3].toInt() and 0xFF))

        // Validate length against actual container size
        val dataRegionSize = container.size - HEADER_SIZE
        if (encLen < GCM_NONCE_LENGTH + 16 || encLen > dataRegionSize) {
            return null
        }
        if (absOffset + LENGTH_FIELD_SIZE + encLen > container.size) {
            return null
        }

        // Extract and decrypt
        val encrypted = container.copyOfRange(absOffset + LENGTH_FIELD_SIZE, absOffset + LENGTH_FIELD_SIZE + encLen)
        val decrypted = aesGcmDecrypt(encrypted, encKey) ?: return null

        // Parse decrypted data
        if (decrypted.isEmpty()) return null

        val padLen = decrypted[0].toInt() and 0xFF
        if (1 + padLen + 4 > decrypted.size) return null

        val payloadLen = ((decrypted[1 + padLen].toInt() and 0xFF) shl 24) or
                ((decrypted[1 + padLen + 1].toInt() and 0xFF) shl 16) or
                ((decrypted[1 + padLen + 2].toInt() and 0xFF) shl 8) or
                (decrypted[1 + padLen + 3].toInt() and 0xFF)

        if (payloadLen < 0 || 1 + padLen + 4 + payloadLen > decrypted.size) {
            return null
        }

        return decrypted.copyOfRange(1 + padLen + 4, 1 + padLen + 4 + payloadLen)
    }
}
