package cash.p.terminal.modules.backuplocal

import cash.p.terminal.core.managers.DeniableEncryptionManager
import cash.p.terminal.modules.backuplocal.fullbackup.FullBackup
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for V4 backup decryption using real encrypted backup files.
 *
 * Test files:
 * - v4_single_password.bin: 4 wallets encrypted with password "1", no duress
 * - v4_dual_password.bin: 4 wallets with password "1", 1 wallet with password "2" (duress)
 *
 * These tests verify the actual encryption/decryption code works correctly
 * without reimplementing the encryption logic.
 */
class BackupV4DecryptionTest {

    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .enableComplexMapKeySerialization()
        .create()

    // region Format Detection Tests

    @Test
    fun `decrypt v4 single password backup returns 4 wallets`() {
        val data = loadBinaryResource("backup/v4_single_password.bin")
        val container = BackupLocalModule.BackupV4Binary.extractContainer(data)!!

        val decrypted = DeniableEncryptionManager.extractMessageFromBytes(container, "1")
        assertNotNull(decrypted)

        val json = String(decrypted!!, Charsets.UTF_8)
        val fullBackup = gson.fromJson(json, FullBackup::class.java)

        assertNotNull("Parsed backup should not be null", fullBackup)
        assertNotNull("Wallets list should not be null", fullBackup.wallets)
        assertEquals(
            "Single password backup should contain 4 wallets",
            4,
            fullBackup.wallets!!.size
        )
    }

    @Test
    fun `decrypt v4 single password backup with wrong password returns null`() {
        val data = loadBinaryResource("backup/v4_single_password.bin")
        val container = BackupLocalModule.BackupV4Binary.extractContainer(data)!!

        val decrypted =
            DeniableEncryptionManager.extractMessageFromBytes(container, "wrongpassword")

        // Wrong password should either return null or return data that doesn't parse as valid JSON
        if (decrypted != null) {
            val json = String(decrypted, Charsets.UTF_8)
            // If data is returned, it should not be valid backup JSON
            try {
                val fullBackup = gson.fromJson(json, FullBackup::class.java)
                // If it parses, wallets should be null or empty (random data won't have valid structure)
                assertTrue(
                    "Wrong password should not decrypt to valid wallet data",
                    fullBackup.wallets == null || fullBackup.wallets!!.isEmpty()
                )
            } catch (e: Exception) {
                // Expected - random decrypted bytes won't be valid JSON
            }
        }
    }

    // endregion

    // region Dual Password (Duress) Decryption Tests (v4_dual_password.bin)

    @Test
    fun `decrypt v4 dual password backup with main password returns 4 wallets`() {
        val data = loadBinaryResource("backup/v4_dual_password.bin")
        val container = BackupLocalModule.BackupV4Binary.extractContainer(data)!!

        val decrypted = DeniableEncryptionManager.extractMessageFromBytes(container, "1")
        assertNotNull("Decryption with main password should succeed", decrypted)

        val json = String(decrypted!!, Charsets.UTF_8)
        val fullBackup = gson.fromJson(json, FullBackup::class.java)

        assertNotNull(fullBackup)
        assertNotNull(fullBackup.wallets)
        assertEquals(
            "Main password should decrypt to 4 wallets",
            4,
            fullBackup.wallets!!.size
        )
    }

    @Test
    fun `decrypt v4 dual password backup with duress password returns 1 wallet`() {
        val data = loadBinaryResource("backup/v4_dual_password.bin")
        val container = BackupLocalModule.BackupV4Binary.extractContainer(data)!!

        val decrypted = DeniableEncryptionManager.extractMessageFromBytes(container, "2")
        assertNotNull("Decryption with duress password should succeed", decrypted)

        val json = String(decrypted!!, Charsets.UTF_8)
        val fullBackup = gson.fromJson(json, FullBackup::class.java)

        assertNotNull(fullBackup)
        assertNotNull(fullBackup.wallets)
        assertEquals(
            "Duress password should decrypt to 1 wallet",
            1,
            fullBackup.wallets!!.size
        )
    }

    // endregion

    // region Helper Functions

    private fun loadBinaryResource(path: String): ByteArray {
        return javaClass.classLoader!!.getResourceAsStream(path)!!
            .use { it.readBytes() }
    }

    // endregion

    // region Hardware Wallet Skip During Restore Tests

    /**
     * Verifies that hardware wallets in backup JSON are properly identified.
     * The actual skip logic is in BackupProvider.decryptedFullBackupWithKey() which
     * checks `if (type is AccountType.HardwareCard) { return@forEach }`.
     *
     * This test verifies that:
     * 1. A backup containing hardware_card type can be parsed
     * 2. The hardware_card type is correctly identified
     */
    @Test
    fun `backup with hardware_card type is parsed correctly`() {
        // Simulated JSON that would be inside a V4 backup with hardware wallet
        val backupJsonWithHardware = """{
            "wallets": [
                {
                    "name": "Regular Wallet",
                    "backup": {
                        "crypto": {
                            "cipher": "aes-128-ctr",
                            "cipherparams": {"iv": "abc"},
                            "ciphertext": "encrypted",
                            "kdf": "scrypt",
                            "kdfparams": {"dklen": 32, "n": 16384, "p": 4, "r": 8, "salt": "pcash"},
                            "mac": "mac"
                        },
                        "id": "wallet-1",
                        "type": "mnemonic",
                        "enabled_wallets": [],
                        "manual_backup": false,
                        "file_backup": true,
                        "timestamp": 1234567890,
                        "version": 2
                    }
                },
                {
                    "name": "Hardware Wallet",
                    "backup": {
                        "crypto": {
                            "cipher": "aes-128-ctr",
                            "cipherparams": {"iv": "def"},
                            "ciphertext": "encrypted_hw",
                            "kdf": "scrypt",
                            "kdfparams": {"dklen": 32, "n": 16384, "p": 4, "r": 8, "salt": "pcash"},
                            "mac": "mac_hw"
                        },
                        "id": "wallet-2",
                        "type": "hardware_card",
                        "enabled_wallets": [],
                        "manual_backup": false,
                        "file_backup": true,
                        "timestamp": 1234567890,
                        "version": 2
                    }
                }
            ],
            "settings": {
                "balance_primary_value": "CoinThenFiat",
                "app_icon": "Main",
                "theme_mode": "System",
                "indicators_shown": false,
                "indicators": {"rsi": [], "ma": [], "macd": []},
                "balance_auto_hide": false,
                "language": "en",
                "launch_screen": "auto",
                "show_market": true,
                "currency": "USD",
                "btc_modes": [],
                "evm_sync_sources": {"selected": [], "custom": []}
            },
            "timestamp": 1234567890,
            "version": 2,
            "id": "backup-id"
        }"""

        val fullBackup = gson.fromJson(backupJsonWithHardware, FullBackup::class.java)

        assertNotNull(fullBackup)
        assertNotNull(fullBackup.wallets)
        assertEquals("Should have 2 wallets in JSON", 2, fullBackup.wallets!!.size)

        // Verify we can identify hardware wallet type
        val regularWallet = fullBackup.wallets!!.find { it.backup.type == "mnemonic" }
        val hardwareWallet = fullBackup.wallets!!.find { it.backup.type == "hardware_card" }

        assertNotNull("Regular wallet should exist", regularWallet)
        assertNotNull("Hardware wallet should be parseable", hardwareWallet)
        assertEquals("Hardware Wallet", hardwareWallet!!.name)

        // Note: The actual skipping happens in BackupProvider.decryptedFullBackupWithKey()
        // which is tested via integration tests since it requires crypto operations
    }

    // endregion
}
