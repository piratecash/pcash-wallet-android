package cash.p.terminal.modules.backuplocal

import cash.p.terminal.core.managers.DeniableEncryptionManager
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.modules.backuplocal.fullbackup.BackupSource
import cash.p.terminal.modules.backuplocal.fullbackup.DecryptedFullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.RestoreOutcome
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Tests for BackupProvider V4 binary backup creation with retry mechanism.
 */
internal class BackupProviderV4BinaryTest : BackupProviderRestoreTestFixture() {

    // region V4 Binary Backup Creation with Retry

    @Test
    fun createFullBackupV4Binary_emptyWalletLists_succeeds() {
        val result = backupProvider.createFullBackupV4Binary(
            accountIds1 = emptyList(),
            passphrase1 = "mainPassword",
            accountIds2 = null,
            passphrase2 = null
        )

        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        // Verify binary format magic bytes "PW4B"
        assertTrue(BackupLocalModule.BackupV4Binary.isBinaryFormat(result))
    }

    @Test
    fun createWalletBackup_litecoinMwebBackup_keepsMetadataAndBirthdayOnMwebEntry() {
        val account = Account(
            id = "account-id",
            name = "Wallet",
            type = AccountType.Mnemonic(List(12) { "abandon" }, ""),
            origin = AccountOrigin.Created,
            level = 0
        )
        val restoreSettings = RestoreSettings().apply {
            this[RestoreSettingType.BirthdayHeight] = "2257920"
        }
        every { settingsManager.settings(account, BlockchainType.Litecoin) } returns restoreSettings
        every { walletStorage.enabledWallets(account.id) } returns listOf(
            EnabledWallet(
                tokenQueryId = "litecoin|derived:bip84",
                accountId = account.id,
                coinName = "Litecoin",
                coinCode = "LTC",
                coinDecimals = 8,
                coinImage = null
            ),
            EnabledWallet(
                tokenQueryId = "litecoin|mweb",
                accountId = account.id,
                coinName = "Litecoin",
                coinCode = "LTC",
                coinDecimals = 8,
                coinImage = null
            )
        )

        val enabledWalletBackups = backupProvider.enabledWalletBackups(account)
        val publicLitecoin = enabledWalletBackups.first {
            it.tokenQueryId == "litecoin|derived:bip84"
        }
        val mwebLitecoin = enabledWalletBackups.first {
            it.tokenQueryId == "litecoin|mweb"
        }

        assertNull(publicLitecoin.settings)
        assertEquals("Litecoin", mwebLitecoin.coinName)
        assertEquals("LTC", mwebLitecoin.coinCode)
        assertEquals(8, mwebLitecoin.decimals)
        assertEquals(
            "2257920",
            mwebLitecoin.settings?.get(RestoreSettingType.BirthdayHeight)
        )
    }

    @Test
    fun createFullBackupV4Binary_dualPasswords_succeedsViaRetryMechanism() {
        // This test verifies the retry mechanism works in BackupProvider
        // Even if passwords derive colliding offsets, retry with new salt should succeed

        val result = backupProvider.createFullBackupV4Binary(
            accountIds1 = emptyList(),
            passphrase1 = "mainPassword123",
            accountIds2 = emptyList(),
            passphrase2 = "duressPassword456"
        )

        assertNotNull(result)
        assertTrue(BackupLocalModule.BackupV4Binary.isBinaryFormat(result))

        // Verify container can be extracted
        val container = BackupLocalModule.BackupV4Binary.extractContainer(result)
        assertNotNull(container)

        // Verify both passwords can decrypt their data
        val data1 = DeniableEncryptionManager.extractMessageFromBytes(container!!, "mainPassword123")
        val data2 = DeniableEncryptionManager.extractMessageFromBytes(container, "duressPassword456")

        assertNotNull("Main password should decrypt data", data1)
        assertNotNull("Duress password should decrypt data", data2)
    }

    @Test
    fun createFullBackupV4Binary_retryMechanism_handlesPotentialCollisions() {
        // Run multiple times to ensure retry mechanism is robust
        repeat(5) { iteration ->
            val result = backupProvider.createFullBackupV4Binary(
                accountIds1 = emptyList(),
                passphrase1 = "password_$iteration",
                accountIds2 = emptyList(),
                passphrase2 = "duress_$iteration"
            )

            assertNotNull("Iteration $iteration should succeed", result)
            assertTrue(BackupLocalModule.BackupV4Binary.isBinaryFormat(result))
        }
    }

    // This test is disabled because it requires ETH-KECCAK-256 algorithm which is only
    // available in Android crypto providers, not in standard JVM unit test environment.
    // The actual wallet encryption with accounts is tested through instrumented tests.
    // @Test
    // fun createFullBackupV4Binary_withAccounts_createsValidBackup() { ... }

    // endregion

    // region Restore filtering

    @Test
    fun restoreSingleWalletBackup_tokenUnknownToMarketKitLegacySource_dropsIt() = runTest {
        curate(usdtQueryId)
        val saved = captureRestoredWallets()

        backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(backedUpWallet(usdtQueryId), backedUpWallet(scamQueryId))
            ),
            approved = emptySet()
        )

        assertEquals(listOf(usdtQueryId), saved.captured.map { it.tokenQueryId })
        assertEquals(restoredAccount.id, saved.captured.single().accountId)
    }

    @Test
    fun restoreSingleWalletBackup_manuallyAddedTokenAuthenticatedSource_raisesReview() = runTest {
        curate(usdtQueryId)

        val outcome = backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(
                    backedUpWallet(usdtQueryId),
                    backedUpWallet(manuallyAddedQueryId).copy(
                        coinName = "My Token",
                        coinCode = "MYT",
                        decimals = 8
                    )
                ),
                source = BackupSource.Authenticated
            )
        )

        val review = outcome as RestoreOutcome.TokensNeedReview
        val declined = review.wallets.single().tokens.single()
        assertEquals(manuallyAddedQueryId, declined.tokenQueryId)
        assertEquals("My Token", declined.coinName)
        assertEquals("MYT", declined.coinCode)
        assertEquals(8, declined.decimals)
        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun restoreSingleWalletBackup_manuallyAddedTokenAuthenticatedSource_approved_restoresItFromFileMetadata() = runTest {
        curate(usdtQueryId)
        val saved = captureRestoredWallets()

        backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(
                    backedUpWallet(usdtQueryId),
                    backedUpWallet(manuallyAddedQueryId).copy(
                        coinName = "My Token",
                        coinCode = "MYT",
                        decimals = 8
                    )
                ),
                source = BackupSource.Authenticated
            ),
            approved = setOf(manuallyAddedQueryId)
        )

        assertEquals(
            listOf(usdtQueryId, manuallyAddedQueryId),
            saved.captured.map { it.tokenQueryId }
        )
        val manuallyAdded = saved.captured.last()
        assertEquals("My Token", manuallyAdded.coinName)
        assertEquals("MYT", manuallyAdded.coinCode)
        assertEquals(8, manuallyAdded.coinDecimals)
        assertNull(manuallyAdded.coinImage)
    }

    @Test
    fun restoreSingleWalletBackup_sameContractInTwoCases_savesOneCuratedRow() = runTest {
        val checksummed = "ethereum|eip20:0xDAC17F958D2ee523a2206206994597C13D831ec7"
        val curated = Token(
            coin = Coin(uid = usdtQueryId, name = "Tether", code = "USDT"),
            blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
            type = requireNotNull(TokenQuery.fromId(usdtQueryId)).tokenType,
            decimals = 6
        )
        // MarketKit's `LIKE '%reference'` fallback ignores ASCII case, so both forms resolve to
        // the same lowercase curated token.
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>()
                .filter { it.id.lowercase() == usdtQueryId }
                .map { curated }
                .distinct()
        }
        val saved = captureRestoredWallets()

        backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(backedUpWallet(checksummed), backedUpWallet(usdtQueryId))
            )
        )

        assertEquals(listOf(usdtQueryId), saved.captured.map { it.tokenQueryId })
    }

    @Test
    fun restoreSingleWalletBackup_backupCarriesSpoofedMetadata_savesCuratedMetadata() = runTest {
        curate(usdtQueryId)
        val saved = captureRestoredWallets()

        backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(
                    backedUpWallet(usdtQueryId).copy(
                        coinName = "Free Airdrop",
                        coinCode = "SCAM",
                        decimals = 18
                    )
                )
            )
        )

        val restored = saved.captured.single()
        assertEquals("Curated", restored.coinName)
        assertEquals("CUR", restored.coinCode)
        assertEquals(6, restored.coinDecimals)
    }

    @Test
    fun restoreSingleWalletBackup_curatedTokenTypeUnsupportedLegacySource_dropsIt() = runTest {
        val (splQueryId, curated) = unsupportedCuratedSplToken()
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().filter { it.id == splQueryId }.map { curated }
        }
        val saved = captureRestoredWallets()

        backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(backedUpWallet(splQueryId, decimals = 6)),
                source = BackupSource.Legacy
            )
        )

        assertTrue(saved.captured.isEmpty())
    }

    @Test
    fun restoreSingleWalletBackup_curatedTokenTypeUnsupportedAuthenticatedSource_restoresWithFileDecimals() = runTest {
        val (splQueryId, curated) = unsupportedCuratedSplToken()
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().filter { it.id == splQueryId }.map { curated }
        }
        val saved = captureRestoredWallets()

        backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(backedUpWallet(splQueryId, decimals = 6)),
                source = BackupSource.Authenticated
            )
        )

        val restored = saved.captured.single()
        assertEquals(splQueryId, restored.tokenQueryId)
        assertEquals(6, restored.coinDecimals)
    }

    @Test
    fun restoreFromV4BinaryBackup_realRoundTrip_restoresCuratedUnsupportedTokenWithFileDecimals() = runTest {
        val passphrase = "correct horse battery staple"
        val accountToBackup = Account(
            id = "backup-account-id",
            name = "Backup",
            type = AccountType.Mnemonic(List(12) { "abandon" }, ""),
            origin = AccountOrigin.Created,
            level = 0
        )
        val (splQueryId, curated) = unsupportedCuratedSplToken()
        every { walletStorage.enabledWallets(accountToBackup.id) } returns listOf(
            EnabledWallet(
                tokenQueryId = splQueryId,
                accountId = accountToBackup.id,
                coinName = "Pyth Network",
                coinCode = "PYTH",
                coinDecimals = 6,
                coinImage = null
            )
        )

        val binary = backupProvider.createSingleWalletBackupV4Binary(accountToBackup, passphrase)

        every { accountFactory.account(any(), any(), any(), any(), any()) } returns restoredAccount
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().filter { it.id == splQueryId }.map { curated }
        }
        val saved = captureRestoredWallets()

        val decrypted = backupProvider.restoreFromV4BinaryBackup(binary, passphrase)

        assertNotNull(decrypted)
        assertEquals(BackupSource.Authenticated, requireNotNull(decrypted).wallets.single().source)

        backupProvider.restoreFullBackup(decrypted, passphrase)

        val restored = saved.captured.single()
        assertEquals(splQueryId, restored.tokenQueryId)
        assertEquals(6, restored.coinDecimals)
    }

    @Test
    fun restoreSingleWalletBackup_walletBackup_dropsTokensUnknownToMarketKit() = runTest {
        curate(usdtQueryId)
        val saved = captureRestoredWallets()
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns restoredAccount
        val backup = mockk<BackupLocalModule.WalletBackup> {
            every { manualBackup } returns true
            every { enabledWallets } returns listOf(
                backedUpWallet(usdtQueryId),
                backedUpWallet(scamQueryId)
            )
        }

        backupProvider.restoreSingleWalletBackup(
            restoredAccount.type, "Restored", backup, BackupSource.Legacy, approved = emptySet()
        )

        assertEquals(listOf(usdtQueryId), saved.captured.map { it.tokenQueryId })
    }

    @Test
    fun restoreSingleWalletBackup_noTokenKnownToMarketKit_restoresAccountWithoutWallets() = runTest {
        val saved = captureRestoredWallets()

        backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(backedUpWallet(scamQueryId))
            ),
            approved = emptySet()
        )

        assertTrue(saved.captured.isEmpty())
        coVerify { accountManager.import(listOf(restoredAccount)) }
    }

    @Test
    fun restoreSingleWalletBackup_uncuratedTokenWithBirthdayHeight_stillRestoresItsSettings() = runTest {
        val saved = captureRestoredWallets()

        backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(
                    backedUpWallet(
                        tokenQueryId = "monero|native",
                        settings = mapOf(RestoreSettingType.BirthdayHeight to "3000000")
                    )
                )
            ),
            approved = emptySet()
        )

        assertTrue(saved.captured.isEmpty())
        verify {
            restoreSettingsManager.save(
                match { it.birthdayHeight == 3_000_000L },
                restoredAccount,
                BlockchainType.Monero
            )
        }
    }

    /** The legacy overload keeps its own settings loop, so it needs its own asymmetry test. */
    @Test
    fun restoreSingleWalletBackup_legacyPathUncuratedTokenWithBirthdayHeight_stillRestoresItsSettings() = runTest {
        val saved = captureRestoredWallets()
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns restoredAccount
        val backup = mockk<BackupLocalModule.WalletBackup> {
            every { manualBackup } returns true
            every { enabledWallets } returns listOf(
                backedUpWallet(
                    tokenQueryId = "monero|native",
                    settings = mapOf(RestoreSettingType.BirthdayHeight to "3000000")
                )
            )
        }

        backupProvider.restoreSingleWalletBackup(
            restoredAccount.type, "Restored", backup, BackupSource.Legacy, approved = emptySet()
        )

        assertTrue(saved.captured.isEmpty())
        verify {
            restoreSettingsManager.save(
                match { it.birthdayHeight == 3_000_000L },
                restoredAccount,
                BlockchainType.Monero
            )
        }
    }

    @Test
    fun restoreFullBackup_curatedLookupFailsOnSecondAccount_persistsNothing() = runTest {
        val secondAccount = restoredAccount.copy(id = "second-account-id", name = "Second")
        every {
            marketKit.tokens(any<List<TokenQuery>>())
        } returns emptyList() andThenThrows IllegalStateException("db down")

        assertFailsWith<IllegalStateException> {
            backupProvider.restoreFullBackup(
                DecryptedFullBackup(
                    wallets = listOf(
                        walletBackupItem(
                            account = restoredAccount,
                            enabledWallets = listOf(
                                backedUpWallet(
                                    tokenQueryId = "monero|native",
                                    settings = mapOf(RestoreSettingType.BirthdayHeight to "3000000")
                                )
                            )
                        ),
                        walletBackupItem(
                            account = secondAccount,
                            enabledWallets = listOf(backedUpWallet(usdtQueryId))
                        )
                    ),
                    watchlist = emptyList(),
                    settings = null,
                    contacts = emptyList()
                ),
                passphrase = "passphrase"
            )
        }

        verify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
        coVerify(exactly = 0) { accountManager.import(any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun restoreFullBackup_multipleAccounts_savesCuratedWalletsOfEveryAccount() = runTest {
        curate(usdtQueryId, daiQueryId)
        val saved = captureRestoredWallets()
        val secondAccount = restoredAccount.copy(id = "second-account-id", name = "Second")

        val outcome = backupProvider.restoreFullBackup(
            DecryptedFullBackup(
                wallets = listOf(restoredAccount to usdtQueryId, secondAccount to daiQueryId)
                    .map { (account, curatedQueryId) ->
                        walletBackupItem(
                            account = account,
                            enabledWallets = listOf(
                                backedUpWallet(curatedQueryId),
                                backedUpWallet(scamQueryId)
                            )
                        )
                    },
                watchlist = emptyList(),
                settings = null,
                contacts = emptyList()
            ),
            passphrase = "passphrase",
            approved = emptyMap()
        )

        assertEquals(RestoreOutcome.Restored, outcome)
        coVerify { accountManager.import(listOf(restoredAccount, secondAccount)) }
        assertEquals(
            listOf(restoredAccount.id to usdtQueryId, secondAccount.id to daiQueryId),
            saved.captured.map { it.accountId to it.tokenQueryId }
        )
    }

    @Test
    fun restoreSingleWalletBackup_moneroAccountWithoutBackedUpSettings_savesHeightFromAccountType() = runTest {
        val moneroAccount = restoredAccount.copy(
            type = AccountType.MnemonicMonero(List(25) { "abandon" }, "", 2_800_000L, "Monero")
        )

        backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                account = moneroAccount,
                enabledWallets = listOf(backedUpWallet("monero|native"))
            ),
            approved = emptySet()
        )

        verify {
            restoreSettingsManager.save(
                match { it.birthdayHeight == 2_800_000L },
                moneroAccount,
                BlockchainType.Monero
            )
        }
    }

    @Test
    fun restoreSingleWalletBackup_malformedTokenQueryId_skipsItAndKeepsRestoringLaterRows() = runTest {
        backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(
                    backedUpWallet(
                        tokenQueryId = "not-a-token-query-id",
                        settings = mapOf(RestoreSettingType.BirthdayHeight to "1")
                    ),
                    backedUpWallet(
                        tokenQueryId = "monero|native",
                        settings = mapOf(RestoreSettingType.BirthdayHeight to "3000000")
                    )
                )
            ),
            approved = emptySet()
        )

        verify(exactly = 1) { restoreSettingsManager.save(any(), any(), any()) }
        verify {
            restoreSettingsManager.save(
                match { it.birthdayHeight == 3_000_000L },
                restoredAccount,
                BlockchainType.Monero
            )
        }
    }

    @Test
    fun restoreSingleWalletBackup_curatedLookupFails_doesNotPersistAccount() = runTest {
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns restoredAccount
        every { marketKit.tokens(any<List<TokenQuery>>()) } throws IllegalStateException("db down")
        val backup = mockk<BackupLocalModule.WalletBackup> {
            every { manualBackup } returns true
            every { enabledWallets } returns listOf(backedUpWallet(usdtQueryId))
        }

        assertFailsWith<IllegalStateException> {
            backupProvider.restoreSingleWalletBackup(
                restoredAccount.type, "Restored", backup, BackupSource.Legacy
            )
        }

        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    // endregion
}
