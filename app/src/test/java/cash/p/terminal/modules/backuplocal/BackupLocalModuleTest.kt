package cash.p.terminal.modules.backuplocal

import cash.p.terminal.wallet.AccountType
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class BackupLocalModuleTest {

    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .enableComplexMapKeySerialization()
        .create()

    private val validMnemonicWords = listOf(
        "abandon", "abandon", "abandon", "abandon", "abandon", "abandon",
        "abandon", "abandon", "abandon", "abandon", "abandon", "about"
    )

    // region Account Type String Mapping

    @Test
    fun `getAccountTypeString returns correct type for Mnemonic`() {
        val accountType = AccountType.Mnemonic(validMnemonicWords, "")
        assertEquals("mnemonic", BackupLocalModule.getAccountTypeString(accountType))
    }

    @Test
    fun `getAccountTypeString returns correct type for EvmPrivateKey`() {
        val accountType = AccountType.EvmPrivateKey(BigInteger.ONE)
        assertEquals("private_key", BackupLocalModule.getAccountTypeString(accountType))
    }

    @Test
    fun `getAccountTypeString returns correct type for HardwareCard`() {
        val accountType = AccountType.HardwareCard(
            cardId = "card123",
            backupCardsCount = 2,
            walletPublicKey = "pubKey",
            signedHashes = 5
        )
        assertEquals("hardware_card", BackupLocalModule.getAccountTypeString(accountType))
    }

    @Test
    fun `getAccountTypeString returns correct type for EvmAddress`() {
        val accountType = AccountType.EvmAddress("0x1234567890abcdef")
        assertEquals("evm_address", BackupLocalModule.getAccountTypeString(accountType))
    }

    // endregion

    // region Mnemonic Encoding/Decoding

    @Test
    fun `getDataForEncryption encodes mnemonic without passphrase correctly`() {
        val accountType = AccountType.Mnemonic(validMnemonicWords, "")
        val data = BackupLocalModule.getDataForEncryption(accountType)
        val expected = validMnemonicWords.joinToString(" ")
        assertEquals(expected, String(data, Charsets.UTF_8))
    }

    @Test
    fun `getDataForEncryption encodes mnemonic with passphrase correctly`() {
        val passphrase = "myPassphrase"
        val accountType = AccountType.Mnemonic(validMnemonicWords, passphrase)
        val data = BackupLocalModule.getDataForEncryption(accountType)
        val expected = validMnemonicWords.joinToString(" ") + "@$passphrase"
        assertEquals(expected, String(data, Charsets.UTF_8))
    }

    @Test
    fun `getAccountTypeFromData decodes mnemonic without passphrase`() = runBlockingSuspend {
        val words = validMnemonicWords.joinToString(" ")
        val data = words.toByteArray(Charsets.UTF_8)

        val result = BackupLocalModule.getAccountTypeFromData("mnemonic", data)

        assertTrue(result is AccountType.Mnemonic)
        val mnemonic = result as AccountType.Mnemonic
        assertEquals(validMnemonicWords, mnemonic.words)
        assertEquals("", mnemonic.passphrase)
    }

    @Test
    fun `getAccountTypeFromData decodes mnemonic with passphrase`() = runBlockingSuspend {
        val passphrase = "testPass"
        val dataString = validMnemonicWords.joinToString(" ") + "@$passphrase"
        val data = dataString.toByteArray(Charsets.UTF_8)

        val result = BackupLocalModule.getAccountTypeFromData("mnemonic", data)

        assertTrue(result is AccountType.Mnemonic)
        val mnemonic = result as AccountType.Mnemonic
        assertEquals(validMnemonicWords, mnemonic.words)
        assertEquals(passphrase, mnemonic.passphrase)
    }

    // endregion

    // region HardwareCard Encoding/Decoding

    @Test
    fun `getDataForEncryption encodes HardwareCard with all fields`() {
        val accountType = AccountType.HardwareCard(
            cardId = "card123",
            backupCardsCount = 2,
            walletPublicKey = "pubKeyABC",
            signedHashes = 10
        )
        val data = BackupLocalModule.getDataForEncryption(accountType)
        val expected = "card123@pubKeyABC@2@10"
        assertEquals(expected, String(data, Charsets.UTF_8))
    }

    @Test
    fun `getAccountTypeFromData decodes HardwareCard with all fields`() = runBlockingSuspend {
        val dataString = "card123@pubKeyABC@2@10"
        val data = dataString.toByteArray(Charsets.UTF_8)

        val result = BackupLocalModule.getAccountTypeFromData("hardware_card", data)

        assertTrue(result is AccountType.HardwareCard)
        val hardwareCard = result as AccountType.HardwareCard
        assertEquals("card123", hardwareCard.cardId)
        assertEquals("pubKeyABC", hardwareCard.walletPublicKey)
        assertEquals(2, hardwareCard.backupCardsCount)
        assertEquals(10, hardwareCard.signedHashes)
    }

    @Test
    fun `getAccountTypeFromData decodes old HardwareCard format without backupCardsCount and signedHashes`() = runBlockingSuspend {
        // Old format: cardId@walletPublicKey (only 2 parts)
        val dataString = "card123@pubKeyABC"
        val data = dataString.toByteArray(Charsets.UTF_8)

        val result = BackupLocalModule.getAccountTypeFromData("hardware_card", data)

        assertTrue(result is AccountType.HardwareCard)
        val hardwareCard = result as AccountType.HardwareCard
        assertEquals("card123", hardwareCard.cardId)
        assertEquals("pubKeyABC", hardwareCard.walletPublicKey)
        assertEquals(0, hardwareCard.backupCardsCount) // defaults to 0
        assertEquals(0, hardwareCard.signedHashes) // defaults to 0
    }

    @Test(expected = IllegalStateException::class)
    fun `getAccountTypeFromData throws for invalid HardwareCard format`() {
        runBlockingSuspend {
            // Invalid format: only 1 part
            val dataString = "card123"
            val data = dataString.toByteArray(Charsets.UTF_8)

            BackupLocalModule.getAccountTypeFromData("hardware_card", data)
        }
    }

    // endregion

    // region EvmPrivateKey Encoding/Decoding

    @Test
    fun `getDataForEncryption encodes EvmPrivateKey as byte array`() {
        val key = BigInteger("123456789")
        val accountType = AccountType.EvmPrivateKey(key)
        val data = BackupLocalModule.getDataForEncryption(accountType)
        // Verify data is not empty and matches expected byte array from BigInteger
        assertTrue(data.isNotEmpty())
        assertTrue(data.contentEquals(key.toByteArray()))
    }

    // endregion

    // region Address Types Encoding/Decoding

    @Test
    fun `getDataForEncryption encodes EvmAddress correctly`() {
        val address = "0x1234567890abcdef1234567890abcdef12345678"
        val accountType = AccountType.EvmAddress(address)
        val data = BackupLocalModule.getDataForEncryption(accountType)
        assertEquals(address, String(data, Charsets.UTF_8))
    }

    @Test
    fun `getAccountTypeFromData decodes EvmAddress correctly`() = runBlockingSuspend {
        val address = "0x1234567890abcdef1234567890abcdef12345678"
        val data = address.toByteArray(Charsets.UTF_8)

        val result = BackupLocalModule.getAccountTypeFromData("evm_address", data)

        assertTrue(result is AccountType.EvmAddress)
        assertEquals(address, (result as AccountType.EvmAddress).address)
    }

    @Test
    fun `getDataForEncryption encodes SolanaAddress correctly`() {
        val address = "SolanaAddress123"
        val accountType = AccountType.SolanaAddress(address)
        val data = BackupLocalModule.getDataForEncryption(accountType)
        assertEquals(address, String(data, Charsets.UTF_8))
    }

    @Test
    fun `getAccountTypeFromData decodes SolanaAddress correctly`() = runBlockingSuspend {
        val address = "SolanaAddress123"
        val data = address.toByteArray(Charsets.UTF_8)

        val result = BackupLocalModule.getAccountTypeFromData("solana_address", data)

        assertTrue(result is AccountType.SolanaAddress)
        assertEquals(address, (result as AccountType.SolanaAddress).address)
    }

    @Test
    fun `getDataForEncryption encodes TronAddress correctly`() {
        val address = "TronAddress123"
        val accountType = AccountType.TronAddress(address)
        val data = BackupLocalModule.getDataForEncryption(accountType)
        assertEquals(address, String(data, Charsets.UTF_8))
    }

    @Test
    fun `getAccountTypeFromData decodes TronAddress correctly`() = runBlockingSuspend {
        val address = "TronAddress123"
        val data = address.toByteArray(Charsets.UTF_8)

        val result = BackupLocalModule.getAccountTypeFromData("tron_address", data)

        assertTrue(result is AccountType.TronAddress)
        assertEquals(address, (result as AccountType.TronAddress).address)
    }

    @Test
    fun `getDataForEncryption encodes TonAddress correctly`() {
        val address = "TonAddress123"
        val accountType = AccountType.TonAddress(address)
        val data = BackupLocalModule.getDataForEncryption(accountType)
        assertEquals(address, String(data, Charsets.UTF_8))
    }

    @Test
    fun `getAccountTypeFromData decodes TonAddress correctly`() = runBlockingSuspend {
        val address = "TonAddress123"
        val data = address.toByteArray(Charsets.UTF_8)

        val result = BackupLocalModule.getAccountTypeFromData("ton_address", data)

        assertTrue(result is AccountType.TonAddress)
        assertEquals(address, (result as AccountType.TonAddress).address)
    }

    // endregion

    // region Version Constant

    @Test
    fun `BACKUP_VERSION is 3`() {
        assertEquals(3, BackupLocalModule.BACKUP_VERSION)
    }

    // endregion

    // region Round-trip Tests

    @Test
    fun `mnemonic encode and decode round-trip without passphrase`() = runBlockingSuspend {
        val original = AccountType.Mnemonic(validMnemonicWords, "")
        val typeString = BackupLocalModule.getAccountTypeString(original)
        val data = BackupLocalModule.getDataForEncryption(original)
        val decoded = BackupLocalModule.getAccountTypeFromData(typeString, data)

        assertTrue(decoded is AccountType.Mnemonic)
        val decodedMnemonic = decoded as AccountType.Mnemonic
        assertEquals(original.words, decodedMnemonic.words)
        assertEquals(original.passphrase, decodedMnemonic.passphrase)
    }

    @Test
    fun `mnemonic encode and decode round-trip with passphrase`() = runBlockingSuspend {
        val original = AccountType.Mnemonic(validMnemonicWords, "secretPass123")
        val typeString = BackupLocalModule.getAccountTypeString(original)
        val data = BackupLocalModule.getDataForEncryption(original)
        val decoded = BackupLocalModule.getAccountTypeFromData(typeString, data)

        assertTrue(decoded is AccountType.Mnemonic)
        val decodedMnemonic = decoded as AccountType.Mnemonic
        assertEquals(original.words, decodedMnemonic.words)
        assertEquals(original.passphrase, decodedMnemonic.passphrase)
    }

    @Test
    fun `HardwareCard encode and decode round-trip`() = runBlockingSuspend {
        val original = AccountType.HardwareCard(
            cardId = "myCard",
            backupCardsCount = 3,
            walletPublicKey = "publicKey123",
            signedHashes = 15
        )
        val typeString = BackupLocalModule.getAccountTypeString(original)
        val data = BackupLocalModule.getDataForEncryption(original)
        val decoded = BackupLocalModule.getAccountTypeFromData(typeString, data)

        assertTrue(decoded is AccountType.HardwareCard)
        val decodedCard = decoded as AccountType.HardwareCard
        assertEquals(original.cardId, decodedCard.cardId)
        assertEquals(original.walletPublicKey, decodedCard.walletPublicKey)
        assertEquals(original.backupCardsCount, decodedCard.backupCardsCount)
        assertEquals(original.signedHashes, decodedCard.signedHashes)
    }

    @Test
    fun `EvmAddress encode and decode round-trip`() = runBlockingSuspend {
        val original = AccountType.EvmAddress("0xABCDEF123456")
        val typeString = BackupLocalModule.getAccountTypeString(original)
        val data = BackupLocalModule.getDataForEncryption(original)
        val decoded = BackupLocalModule.getAccountTypeFromData(typeString, data)

        assertTrue(decoded is AccountType.EvmAddress)
        assertEquals(original.address, (decoded as AccountType.EvmAddress).address)
    }

    // endregion

    private fun <T> runBlockingSuspend(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }

    // region Real Backup File Tests

    @Test
    fun `decode v2 wallet backup file structure`() {
        val json = loadResourceFile("backup/wallet_backup_v2_sample.json")

        val walletBackup = gson.fromJson(json, BackupLocalModule.WalletBackup::class.java)

        assertNotNull(walletBackup)
        assertEquals(2, walletBackup.version)
        assertEquals("mnemonic", walletBackup.type)
        assertTrue(walletBackup.fileBackup)

        // Verify crypto structure
        assertNotNull(walletBackup.crypto)
        assertEquals("aes-128-ctr", walletBackup.crypto.cipher)
        assertEquals("scrypt", walletBackup.crypto.kdf)
        assertNotNull(walletBackup.crypto.cipherparams.iv)
        assertNotNull(walletBackup.crypto.ciphertext)
        assertNotNull(walletBackup.crypto.mac)

        // Verify KDF params
        assertEquals(32, walletBackup.crypto.kdfparams.dklen)
        assertEquals(16384, walletBackup.crypto.kdfparams.n)
        assertEquals(4, walletBackup.crypto.kdfparams.p)
        assertEquals(8, walletBackup.crypto.kdfparams.r)
        assertEquals("pcash", walletBackup.crypto.kdfparams.salt)
    }

    @Test
    fun `decode v2 wallet backup with 5 enabled wallets`() {
        val json = loadResourceFile("backup/wallet_backup_v2_sample.json")
        val walletBackup = gson.fromJson(json, BackupLocalModule.WalletBackup::class.java)

        // Verify 5 wallets present
        assertNotNull(walletBackup.enabledWallets)
        assertEquals(5, walletBackup.enabledWallets!!.size)

        val wallets = walletBackup.enabledWallets!!

        // BTC bip84
        val btcWallet = wallets.find { it.tokenQueryId == "bitcoin|derived:bip84" }
        assertNotNull("BTC bip84 wallet should exist", btcWallet)
        assertEquals("BTC", btcWallet!!.coinCode)
        assertEquals("Bitcoin", btcWallet.coinName)
        assertEquals(8, btcWallet.decimals)

        // ETH
        val ethWallet = wallets.find { it.tokenQueryId == "ethereum|native" }
        assertNotNull("ETH wallet should exist", ethWallet)
        assertEquals("ETH", ethWallet!!.coinCode)
        assertEquals("Ethereum", ethWallet.coinName)
        assertEquals(18, ethWallet.decimals)

        // Monero
        val xmrWallet = wallets.find { it.tokenQueryId == "monero|native" }
        assertNotNull("Monero wallet should exist", xmrWallet)
        assertEquals("XMR", xmrWallet!!.coinCode)
        assertEquals("Monero", xmrWallet.coinName)
        assertEquals(12, xmrWallet.decimals)
        assertNotNull("Monero should have birthday_height setting", xmrWallet.settings)

        // ZEC shielded
        val zecShieldedWallet = wallets.find { it.tokenQueryId == "zcash|address_spec_type:shielded" }
        assertNotNull("ZEC shielded wallet should exist", zecShieldedWallet)
        assertEquals("ZEC", zecShieldedWallet!!.coinCode)
        assertEquals("Zcash", zecShieldedWallet.coinName)
        assertEquals(8, zecShieldedWallet.decimals)
        assertNotNull("ZEC shielded should have birthday_height setting", zecShieldedWallet.settings)

        // ZEC BEP20
        val zecBep20Wallet = wallets.find {
            it.tokenQueryId.contains("binance-smart-chain") && it.coinCode == "ZEC"
        }
        assertNotNull("ZEC BEP20 wallet should exist", zecBep20Wallet)
        assertEquals("ZEC", zecBep20Wallet!!.coinCode)
        assertEquals("Zcash", zecBep20Wallet.coinName)
        assertEquals(18, zecBep20Wallet.decimals)
    }

    @Test
    fun `decode v2 full backup with 3 wallets`() {
        val json = loadResourceFile("backup/full_backup_v2_sample.json")
        val fullBackup = gson.fromJson(json, cash.p.terminal.modules.backuplocal.fullbackup.FullBackup::class.java)

        // Verify structure
        assertNotNull(fullBackup)
        assertEquals(2, fullBackup.version)
        assertNotNull(fullBackup.wallets)
        assertEquals(3, fullBackup.wallets!!.size)

        // Wallet 1: Watch Wallet (evm_address) - 7 assets
        val watchWallet = fullBackup.wallets!!.find { it.name == "Watch Wallet 1" }
        assertNotNull("Watch Wallet 1 should exist", watchWallet)
        assertEquals("evm_address", watchWallet!!.backup.type)
        assertEquals(7, watchWallet.backup.enabledWallets!!.size)

        // Wallet 2: Wallet 4 (mnemonic) - 5 assets
        val wallet4 = fullBackup.wallets!!.find { it.name == "Wallet 4" }
        assertNotNull("Wallet 4 should exist", wallet4)
        assertEquals("mnemonic", wallet4!!.backup.type)
        assertEquals(5, wallet4.backup.enabledWallets!!.size)

        // Wallet 3: Wallet 5 (mnemonic_monero) - 1 asset (Monero)
        val wallet5 = fullBackup.wallets!!.find { it.name == "Wallet 5" }
        assertNotNull("Wallet 5 should exist", wallet5)
        assertEquals("mnemonic_monero", wallet5!!.backup.type)
        assertEquals(1, wallet5.backup.enabledWallets!!.size)
        assertEquals("XMR", wallet5.backup.enabledWallets!![0].coinCode)
        assertEquals("monero|native", wallet5.backup.enabledWallets!![0].tokenQueryId)
    }

    private fun loadResourceFile(path: String): String {
        return javaClass.classLoader!!.getResourceAsStream(path)!!
            .bufferedReader()
            .use { it.readText() }
    }

    // endregion
}
