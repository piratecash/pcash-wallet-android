package cash.p.terminal.modules.backuplocal

import android.util.Base64
import cash.p.terminal.core.managers.EncryptDecryptManager
import cash.p.terminal.modules.backuplocal.fullbackup.FullBackup
import com.google.gson.GsonBuilder
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupProviderV3Test {

    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .enableComplexMapKeySerialization()
        .create()

    @Before
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    // region Real Backup File Decryption Tests

    @Test
    fun `decrypt v2 wallet backup and verify mnemonic`() {
        val json = loadResourceFile("backup/wallet_backup_v2_sample.json")
        val walletBackup = gson.fromJson(json, BackupLocalModule.WalletBackup::class.java)
        val crypto = walletBackup.crypto

        // Derive key from password
        val key = EncryptDecryptManager.getKey(TEST_BACKUP_PASSWORD, crypto.kdfparams)
        assertNotNull("Key should be derived successfully", key)

        // Decrypt and verify mnemonic
        val encryptDecryptManager = EncryptDecryptManager()
        val decryptedData = encryptDecryptManager.decrypt(
            crypto.ciphertext,
            key!!,
            crypto.cipherparams.iv
        )

        assertNotNull("Decrypted data should not be null", decryptedData)
        assertTrue("Decrypted data should not be empty", decryptedData.isNotEmpty())

        val mnemonic = String(decryptedData, Charsets.UTF_8).split("@")[0]
        assertEquals(
            "truth jaguar roof task always top hybrid rookie across bid punch ranch",
            mnemonic
        )
    }

    @Test
    fun `decrypt v2 full backup and verify monero 25-word mnemonic`() {
        val json = loadResourceFile("backup/full_backup_v2_sample.json")
        val fullBackup = gson.fromJson(json, cash.p.terminal.modules.backuplocal.fullbackup.FullBackup::class.java)

        // Find Wallet 5 (mnemonic_monero type with 1 asset)
        val wallet5 = fullBackup.wallets!!.find { it.name == "Wallet 5" }
        assertNotNull("Wallet 5 should exist", wallet5)
        assertEquals("mnemonic_monero", wallet5!!.backup.type)
        assertEquals(1, wallet5.backup.enabledWallets!!.size)

        val crypto = wallet5.backup.crypto

        // Derive key from password
        val key = EncryptDecryptManager.getKey(TEST_BACKUP_PASSWORD, crypto.kdfparams)
        assertNotNull("Key should be derived successfully", key)

        // Decrypt and verify 25-word Monero mnemonic
        val encryptDecryptManager = EncryptDecryptManager()
        val decryptedData = encryptDecryptManager.decrypt(
            crypto.ciphertext,
            key!!,
            crypto.cipherparams.iv
        )

        assertNotNull("Decrypted data should not be null", decryptedData)
        assertTrue("Decrypted data should not be empty", decryptedData.isNotEmpty())

        // Monero mnemonic format: "word1 word2 ... word25@height"
        val decryptedString = String(decryptedData, Charsets.UTF_8)
        val mnemonic = decryptedString.split("@")[0]
        val words = mnemonic.split(" ")

        // Verify 25 words
        assertEquals("Monero mnemonic should have 25 words", 25, words.size)

        // Verify exact mnemonic
        val expectedMnemonic = "owed soda imagine yawning divers dosage medicate duckling unbending dewdrop request ditch richly physics sleepless today lettuce seventh deodorant tarnished bounced mohawk annoyed beer duckling"
        assertEquals(expectedMnemonic, mnemonic)
    }

    private fun loadResourceFile(path: String): String {
        return javaClass.classLoader!!.getResourceAsStream(path)!!
            .bufferedReader()
            .use { it.readText() }
    }

    // endregion

    // region BackupV3 Data Class Tests

    @Test
    fun `BackupV3 serializes correctly`() {
        val backupV3 = BackupLocalModule.BackupV3(
            version = 3,
            encrypted = "encryptedDataBase64"
        )

        val json = gson.toJson(backupV3)

        assertTrue(json.contains("\"version\":3"))
        assertTrue(json.contains("\"encrypted\":\"encryptedDataBase64\""))
    }

    @Test
    fun `BackupV3 deserializes correctly`() {
        val json = """{"version":3,"encrypted":"testEncryptedData"}"""

        val backupV3 = gson.fromJson(json, BackupLocalModule.BackupV3::class.java)

        assertEquals(3, backupV3.version)
        assertEquals("testEncryptedData", backupV3.encrypted)
    }

    // endregion

    // region V3 Format Detection Tests

    @Test
    fun `isV3Format returns true for valid V3 backup`() {
        val v3Json = """{"version":3,"encrypted":"someBase64Data"}"""
        val parsed = gson.fromJson(v3Json, BackupLocalModule.BackupV3::class.java)

        val isV3 = parsed.version == BackupLocalModule.BACKUP_VERSION && parsed.encrypted.isNotEmpty()

        assertTrue(isV3)
    }

    @Test
    fun `isV3Format returns false for V2 backup`() {
        val v2Json = """{"version":2,"wallets":[],"settings":{}}"""
        val parsed = try {
            val backup = gson.fromJson(v2Json, BackupLocalModule.BackupV3::class.java)
            backup.version == BackupLocalModule.BACKUP_VERSION && !backup.encrypted.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }

        assertTrue(!parsed)
    }

    @Test
    fun `isV3Format returns false for old single wallet backup`() {
        val oldBackupJson = """{
            "crypto": {
                "cipher": "aes-128-ctr",
                "cipherparams": {"iv": "abc123"},
                "ciphertext": "encrypted",
                "kdf": "scrypt",
                "kdfparams": {"dklen": 32, "n": 16384, "p": 4, "r": 8, "salt": "pcash"},
                "mac": "mac123"
            },
            "id": "wallet-id",
            "type": "mnemonic",
            "version": 2
        }"""

        val parsed = try {
            val backup = gson.fromJson(oldBackupJson, BackupLocalModule.BackupV3::class.java)
            backup.version == BackupLocalModule.BACKUP_VERSION && !backup.encrypted.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }

        assertTrue(!parsed)
    }

    // endregion

    // region Old Format Compatibility Tests

    @Test
    fun `old WalletBackup format can be parsed`() {
        val oldBackupJson = """{
            "crypto": {
                "cipher": "aes-128-ctr",
                "cipherparams": {"iv": "abc123def456"},
                "ciphertext": "encryptedText",
                "kdf": "scrypt",
                "kdfparams": {"dklen": 32, "n": 16384, "p": 4, "r": 8, "salt": "pcash"},
                "mac": "macValue"
            },
            "id": "wallet-id-hash",
            "type": "mnemonic",
            "enabled_wallets": [],
            "manual_backup": true,
            "file_backup": false,
            "timestamp": 1234567890,
            "version": 2
        }"""

        val walletBackup = gson.fromJson(oldBackupJson, BackupLocalModule.WalletBackup::class.java)

        assertNotNull(walletBackup)
        assertEquals("wallet-id-hash", walletBackup.id)
        assertEquals("mnemonic", walletBackup.type)
        assertEquals(2, walletBackup.version)
        assertTrue(walletBackup.manualBackup)
        assertEquals("aes-128-ctr", walletBackup.crypto.cipher)
    }

    @Test
    fun `old FullBackup format can be parsed`() {
        val oldFullBackupJson = """{
            "wallets": [],
            "watchlist": ["coin1", "coin2"],
            "settings": {
                "balance_primary_value": "CoinValue",
                "app_icon": "Main",
                "theme_mode": "Dark",
                "indicators_shown": false,
                "indicators": {"rsi": [], "ma": [], "macd": []},
                "balance_auto_hide": false,
                "language": "en",
                "launch_screen": "Auto",
                "show_market": true,
                "currency": "USD",
                "btc_modes": [],
                "evm_sync_sources": {"selected": [], "custom": []}
            },
            "timestamp": 1234567890,
            "version": 2,
            "id": "backup-id"
        }"""

        val fullBackup = gson.fromJson(oldFullBackupJson, FullBackup::class.java)

        assertNotNull(fullBackup)
        assertEquals("backup-id", fullBackup.id)
        assertEquals(2, fullBackup.version)
        assertEquals(listOf("coin1", "coin2"), fullBackup.watchlist)
        assertNotNull(fullBackup.settings)
    }

    // endregion

    // region V3 Format Structure Tests

    @Test
    fun `V3 format has correct structure after serialization`() {
        val backupV3 = BackupLocalModule.BackupV3(
            version = 3,
            encrypted = "base64EncodedCryptoJson"
        )

        val json = gson.toJson(backupV3)
        val parsed = gson.fromJson(json, BackupLocalModule.BackupV3::class.java)

        assertEquals(3, parsed.version)
        assertEquals("base64EncodedCryptoJson", parsed.encrypted)
    }

    @Test
    fun `BackupCrypto structure is correct`() {
        val crypto = BackupLocalModule.BackupCrypto(
            cipher = "aes-128-ctr",
            cipherparams = BackupLocalModule.CipherParams(iv = "0123456789abcdef"),
            ciphertext = "encryptedContent",
            kdf = "scrypt",
            kdfparams = BackupLocalModule.KdfParams(dklen = 32, n = 16384, p = 4, r = 8, salt = "pcash"),
            mac = "macHash"
        )

        val json = gson.toJson(crypto)
        val parsed = gson.fromJson(json, BackupLocalModule.BackupCrypto::class.java)

        assertEquals("aes-128-ctr", parsed.cipher)
        assertEquals("0123456789abcdef", parsed.cipherparams.iv)
        assertEquals("encryptedContent", parsed.ciphertext)
        assertEquals("scrypt", parsed.kdf)
        assertEquals(32, parsed.kdfparams.dklen)
        assertEquals(16384, parsed.kdfparams.n)
        assertEquals(4, parsed.kdfparams.p)
        assertEquals(8, parsed.kdfparams.r)
        assertEquals("pcash", parsed.kdfparams.salt)
        assertEquals("macHash", parsed.mac)
    }

    // endregion

    // region Default Format Tests

    @Test
    fun `BACKUP_VERSION constant equals 3`() {
        assertEquals(3, BackupLocalModule.BACKUP_VERSION)
    }

    @Test
    fun `kdfDefault has correct parameters`() {
        val kdfParams = BackupLocalModule.kdfDefault

        assertEquals(32, kdfParams.dklen)
        assertEquals(16384, kdfParams.n)
        assertEquals(4, kdfParams.p)
        assertEquals(8, kdfParams.r)
    }

    // endregion

    // region Edge Cases

    @Test
    fun `empty encrypted field is handled`() {
        val json = """{"version":3,"encrypted":""}"""
        val parsed = gson.fromJson(json, BackupLocalModule.BackupV3::class.java)

        assertEquals(3, parsed.version)
        assertEquals("", parsed.encrypted)

        // Should not be considered valid V3
        val isValidV3 = parsed.version == BackupLocalModule.BACKUP_VERSION && parsed.encrypted.isNotEmpty()
        assertTrue(!isValidV3)
    }

    @Test
    fun `null fields in partial JSON are handled gracefully`() {
        val partialJson = """{"version":3}"""
        val parsed = gson.fromJson(partialJson, BackupLocalModule.BackupV3::class.java)

        assertEquals(3, parsed.version)
        assertNull(parsed.encrypted)
    }

    // endregion

    // region Alignment Padding Tests

    @Test
    fun `align_payload field is ignored during deserialization`() {
        // JSON with align_payload should deserialize correctly, ignoring the padding
        val jsonWithPadding = """{
            "crypto": {
                "cipher": "aes-128-ctr",
                "cipherparams": {"iv": "abc123def456"},
                "ciphertext": "encryptedText",
                "kdf": "scrypt",
                "kdfparams": {"dklen": 32, "n": 16384, "p": 4, "r": 8, "salt": "pcash"},
                "mac": "macValue"
            },
            "id": "wallet-id-hash",
            "type": "mnemonic",
            "enabled_wallets": [],
            "manual_backup": true,
            "file_backup": false,
            "timestamp": 1234567890,
            "version": 2,
            "align_payload": "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        }"""

        val walletBackup = gson.fromJson(jsonWithPadding, BackupLocalModule.WalletBackup::class.java)

        // Verify all fields are parsed correctly
        assertNotNull(walletBackup)
        assertEquals("wallet-id-hash", walletBackup.id)
        assertEquals("mnemonic", walletBackup.type)
        assertEquals(2, walletBackup.version)
        assertTrue(walletBackup.manualBackup)
        // align_payload is simply ignored - no field in WalletBackup class
    }

    @Test
    fun `FullBackup with align_payload deserializes correctly`() {
        val jsonWithPadding = """{
            "wallets": [],
            "watchlist": ["coin1"],
            "settings": {
                "balance_primary_value": "CoinValue",
                "app_icon": "Main",
                "theme_mode": "Dark",
                "indicators_shown": false,
                "indicators": {"rsi": [], "ma": [], "macd": []},
                "balance_auto_hide": false,
                "language": "en",
                "launch_screen": "Auto",
                "show_market": true,
                "currency": "USD",
                "btc_modes": [],
                "evm_sync_sources": {"selected": [], "custom": []}
            },
            "timestamp": 1234567890,
            "version": 2,
            "id": "backup-id",
            "align_payload": "RandomPaddingDataHereToReachTargetSize12345"
        }"""

        val fullBackup = gson.fromJson(jsonWithPadding, cash.p.terminal.modules.backuplocal.fullbackup.FullBackup::class.java)

        assertNotNull(fullBackup)
        assertEquals("backup-id", fullBackup.id)
        assertEquals(2, fullBackup.version)
        assertEquals(listOf("coin1"), fullBackup.watchlist)
        // align_payload is ignored
    }

    // endregion
}

/**
 * Test password for wallet_backup_v2_sample.json is "1"
 */
private const val TEST_BACKUP_PASSWORD = "1"
