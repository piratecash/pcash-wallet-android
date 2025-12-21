package cash.p.terminal.core.managers

import android.util.Base64
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Test helper extension - not in production code
private val SeedPhraseQrCrypto.DecryptedSeed.isMonero: Boolean
    get() = words.size == 25 && height != null

class SeedPhraseQrCryptoTest {

    private lateinit var crypto: SeedPhraseQrCrypto
    private lateinit var timePasswordProvider: TimePasswordProvider

    @Before
    fun setup() {
        // Mock Android Base64 with Java Base64
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }

        // Use real TimePasswordProvider for most tests
        timePasswordProvider = TimePasswordProvider()
        crypto = SeedPhraseQrCrypto(timePasswordProvider)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // Test data
    private val words12 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")
    private val words15 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon address".split(" ")
    private val words18 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon agent".split(" ")
    private val words21 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art".split(" ")
    private val words24 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art".split(" ")
    private val words25Monero = "tavern total bail plutonium faked faster beneath reinvest syndrome dagger razor nobody acoustic tubes people germs myriad next victim sipped oasis dagger razor acoustic acoustic".split(" ")

    // ==================== BIP39 Encryption/Decryption Tests ====================

    @Test
    fun `encrypt and decrypt 12 word seed without passphrase`() {
        val encrypted = crypto.encrypt(words12, "")
        assertTrue(encrypted.startsWith(SeedPhraseQrCrypto.QR_PREFIX))

        val result = crypto.decrypt(encrypted)
        assertTrue(result.isSuccess)

        val decrypted = result.getOrNull()
        assertNotNull(decrypted)
        assertEquals(words12, decrypted!!.words)
        assertEquals("", decrypted.passphrase)
        assertNull(decrypted.height)
        assertFalse(decrypted.isMonero)
    }

    @Test
    fun `encrypt and decrypt 12 word seed with passphrase`() {
        val passphrase = "mySecretPassphrase123"
        val encrypted = crypto.encrypt(words12, passphrase)

        val result = crypto.decrypt(encrypted)
        assertTrue(result.isSuccess)

        val decrypted = result.getOrNull()!!
        assertEquals(words12, decrypted.words)
        assertEquals(passphrase, decrypted.passphrase)
        assertNull(decrypted.height)
        assertFalse(decrypted.isMonero)
    }

    @Test
    fun `encrypt and decrypt 15 word seed`() {
        val encrypted = crypto.encrypt(words15, "")
        val result = crypto.decrypt(encrypted)

        assertTrue(result.isSuccess)
        assertEquals(words15, result.getOrNull()!!.words)
    }

    @Test
    fun `encrypt and decrypt 18 word seed`() {
        val encrypted = crypto.encrypt(words18, "")
        val result = crypto.decrypt(encrypted)

        assertTrue(result.isSuccess)
        assertEquals(words18, result.getOrNull()!!.words)
    }

    @Test
    fun `encrypt and decrypt 21 word seed`() {
        val encrypted = crypto.encrypt(words21, "")
        val result = crypto.decrypt(encrypted)

        assertTrue(result.isSuccess)
        assertEquals(words21, result.getOrNull()!!.words)
    }

    @Test
    fun `encrypt and decrypt 24 word seed`() {
        val encrypted = crypto.encrypt(words24, "")
        val result = crypto.decrypt(encrypted)

        assertTrue(result.isSuccess)
        assertEquals(words24, result.getOrNull()!!.words)
    }

    // ==================== Monero (25 words) Tests ====================

    @Test
    fun `encrypt and decrypt 25 word Monero seed with height`() {
        val height = 2500000L
        val encrypted = crypto.encrypt(words25Monero, "", height)

        assertTrue(encrypted.startsWith(SeedPhraseQrCrypto.QR_PREFIX))

        val result = crypto.decrypt(encrypted)
        assertTrue(result.isSuccess)

        val decrypted = result.getOrNull()!!
        assertEquals(words25Monero, decrypted.words)
        assertEquals("", decrypted.passphrase)
        assertEquals(height, decrypted.height)
        assertTrue(decrypted.isMonero)
    }

    @Test
    fun `25 word seed without height fails decryption`() {
        // Encrypt without height (simulating old format or error)
        val encrypted = crypto.encrypt(words25Monero, "", null)

        val result = crypto.decrypt(encrypted)
        // Should fail because 25 words require height
        assertTrue(result.isFailure)
    }

    @Test
    fun `Monero seed with zero height`() {
        val height = 0L
        val encrypted = crypto.encrypt(words25Monero, "", height)

        val result = crypto.decrypt(encrypted)
        assertTrue(result.isSuccess)

        val decrypted = result.getOrNull()!!
        assertEquals(height, decrypted.height)
    }

    @Test
    fun `Monero seed with large height`() {
        val height = 9999999999L
        val encrypted = crypto.encrypt(words25Monero, "", height)

        val result = crypto.decrypt(encrypted)
        assertTrue(result.isSuccess)
        assertEquals(height, result.getOrNull()!!.height)
    }

    // ==================== QR Format Tests ====================

    @Test
    fun `encrypted string has correct prefix`() {
        val encrypted = crypto.encrypt(words12, "")
        assertTrue(encrypted.startsWith("seed:"))
    }

    @Test
    fun `decrypt fails for invalid prefix`() {
        val invalidQr = "invalid:somebase64data"
        val result = crypto.decrypt(invalidQr)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `decrypt fails for empty string`() {
        val result = crypto.decrypt("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `decrypt fails for prefix only`() {
        val result = crypto.decrypt("seed:")
        assertTrue(result.isFailure)
    }

    @Test
    fun `decrypt fails for invalid base64`() {
        val result = crypto.decrypt("seed:not-valid-base64!!!")
        assertTrue(result.isFailure)
    }

    // ==================== Passphrase Edge Cases ====================

    @Test
    fun `passphrase with special characters`() {
        val passphrase = "p@ss!w0rd#\$%^&*()"
        val encrypted = crypto.encrypt(words12, passphrase)

        val result = crypto.decrypt(encrypted)
        assertTrue(result.isSuccess)
        assertEquals(passphrase, result.getOrNull()!!.passphrase)
    }

    @Test
    fun `passphrase with unicode characters`() {
        val passphrase = "密码пароль🔐"
        val encrypted = crypto.encrypt(words12, passphrase)

        val result = crypto.decrypt(encrypted)
        assertTrue(result.isSuccess)
        assertEquals(passphrase, result.getOrNull()!!.passphrase)
    }

    @Test
    fun `passphrase containing delimiter character`() {
        // The delimiter is @, test that it's handled correctly
        val passphrase = "user@domain.com"
        val encrypted = crypto.encrypt(words12, passphrase)

        val result = crypto.decrypt(encrypted)
        assertTrue(result.isSuccess)
        assertEquals(passphrase, result.getOrNull()!!.passphrase)
    }

    @Test
    fun `passphrase containing height delimiter`() {
        // The height delimiter is |, test that it's handled correctly in passphrase
        val passphrase = "test|pipe|characters"
        val encrypted = crypto.encrypt(words12, passphrase)

        val result = crypto.decrypt(encrypted)
        assertTrue(result.isSuccess)
        assertEquals(passphrase, result.getOrNull()!!.passphrase)
    }

    // ==================== Encryption Uniqueness Tests ====================

    @Test
    fun `same input produces different encrypted outputs due to random IV`() {
        val encrypted1 = crypto.encrypt(words12, "")
        val encrypted2 = crypto.encrypt(words12, "")

        // Different IVs should produce different ciphertexts
        assertTrue(encrypted1 != encrypted2)

        // But both should decrypt to the same value
        val decrypted1 = crypto.decrypt(encrypted1).getOrNull()!!
        val decrypted2 = crypto.decrypt(encrypted2).getOrNull()!!

        assertEquals(decrypted1.words, decrypted2.words)
        assertEquals(decrypted1.passphrase, decrypted2.passphrase)
    }

    // ==================== Invalid Word Count Tests ====================

    @Test
    fun `decrypt fails for 11 words`() {
        val words11 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")
        val encrypted = crypto.encrypt(words11, "")
        val result = crypto.decrypt(encrypted)
        assertTrue(result.isFailure)
    }

    @Test
    fun `decrypt fails for 13 words`() {
        val words13 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")
        val encrypted = crypto.encrypt(words13, "")
        val result = crypto.decrypt(encrypted)
        assertTrue(result.isFailure)
    }

    @Test
    fun `decrypt fails for 26 words`() {
        val words26 = (words25Monero + listOf("extra")).toList()
        val encrypted = crypto.encrypt(words26, "", 100L)
        val result = crypto.decrypt(encrypted)
        assertTrue(result.isFailure)
    }

    // ==================== Time-Based Validity Tests ====================

    /**
     * Creates a SeedPhraseQrCrypto with a mocked TimePasswordProvider.
     * The mock simulates time offsets by returning different passwords.
     *
     * @param encryptOffset The hour offset to use during encryption
     * @param decryptOffset The hour offset to use during decryption (simulates "current" time)
     */
    private fun createCryptoWithTimeOffset(
        encryptOffset: Int,
        decryptOffset: Int
    ): Pair<SeedPhraseQrCrypto, SeedPhraseQrCrypto> {
        val realProvider = TimePasswordProvider()
        val basePassword = realProvider.generateTimePassword(0)

        // Encryption provider - returns password at encryptOffset
        val encryptProvider = mockk<TimePasswordProvider>()
        every { encryptProvider.generateTimePassword(any()) } answers {
            val requestedOffset = firstArg<Int>()
            realProvider.generateTimePassword(encryptOffset + requestedOffset)
        }

        // Decryption provider - returns password at decryptOffset
        val decryptProvider = mockk<TimePasswordProvider>()
        every { decryptProvider.generateTimePassword(any()) } answers {
            val requestedOffset = firstArg<Int>()
            realProvider.generateTimePassword(decryptOffset + requestedOffset)
        }

        return SeedPhraseQrCrypto(encryptProvider) to SeedPhraseQrCrypto(decryptProvider)
    }

    @Test
    fun `decrypt succeeds for QR encrypted at current hour`() {
        val (encryptCrypto, decryptCrypto) = createCryptoWithTimeOffset(0, 0)

        val encrypted = encryptCrypto.encrypt(words12, "")
        val result = decryptCrypto.decrypt(encrypted)

        assertTrue("Decrypt should succeed for current hour", result.isSuccess)
        assertEquals(words12, result.getOrNull()!!.words)
    }

    @Test
    fun `decrypt succeeds for QR encrypted 1 hour ago`() {
        // Encrypt at hour -1, decrypt at hour 0
        val (encryptCrypto, decryptCrypto) = createCryptoWithTimeOffset(-1, 0)

        val encrypted = encryptCrypto.encrypt(words12, "")
        val result = decryptCrypto.decrypt(encrypted)

        assertTrue("Decrypt should succeed for -1 hour", result.isSuccess)
        assertEquals(words12, result.getOrNull()!!.words)
    }

    @Test
    fun `decrypt succeeds for QR encrypted 1 hour in future`() {
        // Encrypt at hour +1, decrypt at hour 0
        val (encryptCrypto, decryptCrypto) = createCryptoWithTimeOffset(+1, 0)

        val encrypted = encryptCrypto.encrypt(words12, "")
        val result = decryptCrypto.decrypt(encrypted)

        assertTrue("Decrypt should succeed for +1 hour", result.isSuccess)
        assertEquals(words12, result.getOrNull()!!.words)
    }

    @Test
    fun `decrypt fails for QR encrypted 2 hours ago`() {
        // Encrypt at hour -2, decrypt at hour 0
        val (encryptCrypto, decryptCrypto) = createCryptoWithTimeOffset(-2, 0)

        val encrypted = encryptCrypto.encrypt(words12, "")
        val result = decryptCrypto.decrypt(encrypted)

        assertTrue("Decrypt should fail for -2 hours (expired)", result.isFailure)
    }

    @Test
    fun `decrypt fails for QR encrypted 2 hours in future`() {
        // Encrypt at hour +2, decrypt at hour 0
        val (encryptCrypto, decryptCrypto) = createCryptoWithTimeOffset(+2, 0)

        val encrypted = encryptCrypto.encrypt(words12, "")
        val result = decryptCrypto.decrypt(encrypted)

        assertTrue("Decrypt should fail for +2 hours (not yet valid)", result.isFailure)
    }

    @Test
    fun `decrypt fails for QR encrypted 3 hours ago`() {
        val (encryptCrypto, decryptCrypto) = createCryptoWithTimeOffset(-3, 0)

        val encrypted = encryptCrypto.encrypt(words12, "")
        val result = decryptCrypto.decrypt(encrypted)

        assertTrue("Decrypt should fail for -3 hours (expired)", result.isFailure)
    }

    @Test
    fun `decrypt fails for QR encrypted 24 hours ago`() {
        val (encryptCrypto, decryptCrypto) = createCryptoWithTimeOffset(-24, 0)

        val encrypted = encryptCrypto.encrypt(words12, "")
        val result = decryptCrypto.decrypt(encrypted)

        assertTrue("Decrypt should fail for -24 hours (expired)", result.isFailure)
    }

    @Test
    fun `time-based validation works for Monero seeds too`() {
        // Encrypt Monero seed 1 hour ago - should work
        val (encryptValid, decryptValid) = createCryptoWithTimeOffset(-1, 0)
        val encryptedValid = encryptValid.encrypt(words25Monero, "", 2500000L)
        val resultValid = decryptValid.decrypt(encryptedValid)

        assertTrue("Monero seed from -1 hour should decrypt", resultValid.isSuccess)
        assertTrue(resultValid.getOrNull()!!.isMonero)

        // Encrypt Monero seed 2 hours ago - should fail
        val (encryptExpired, decryptExpired) = createCryptoWithTimeOffset(-2, 0)
        val encryptedExpired = encryptExpired.encrypt(words25Monero, "", 2500000L)
        val resultExpired = decryptExpired.decrypt(encryptedExpired)

        assertTrue("Monero seed from -2 hours should fail", resultExpired.isFailure)
    }
}
