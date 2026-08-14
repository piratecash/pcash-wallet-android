package cash.p.terminal.modules.backuplocal

import cash.p.terminal.core.managers.DeclinedToken
import cash.p.terminal.modules.backuplocal.fullbackup.BackupSource
import cash.p.terminal.modules.backuplocal.fullbackup.DecryptedFullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.RestoreOutcome
import cash.p.terminal.modules.backuplocal.fullbackup.WalletDeclinedTokens
import cash.p.terminal.modules.contacts.model.Contact
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two-phase restore outcome: a row that needs the user's approval comes back as
 * [RestoreOutcome.TokensNeedReview] instead of being written or dropped, and a follow-up call
 * with the user's approval (or decline) completes it.
 */
internal class BackupProviderDeclinedTokensTest : BackupProviderRestoreTestFixture() {

    @Test
    fun restoreSingleWalletBackup_legacySourceManuallyAddedToken_returnsTokensNeedReviewAndSavesNothing() = runTest {
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns restoredAccount
        val backup = mockk<BackupLocalModule.WalletBackup> {
            every { manualBackup } returns true
            every { enabledWallets } returns listOf(backedUpWallet(manuallyAddedQueryId))
        }

        val outcome = backupProvider.restoreSingleWalletBackup(
            restoredAccount.type, "Restored", backup, BackupSource.Legacy
        )

        assertEquals(
            RestoreOutcome.TokensNeedReview(
                listOf(
                    WalletDeclinedTokens(
                        restoredAccount.id,
                        "Restored",
                        listOf(DeclinedToken(manuallyAddedQueryId, "Tether", "USDT", decimals = 6))
                    )
                )
            ),
            outcome
        )
        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { accountManager.import(any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
        verify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    @Test
    fun restoreFullBackup_onlyLastWalletHasDeclinedToken_touchesNoWriteSite() = runTest {
        curate(usdtQueryId)
        val secondAccount = restoredAccount.copy(id = "second-account-id", name = "Second")

        val outcome = backupProvider.restoreFullBackup(
            DecryptedFullBackup(
                wallets = listOf(
                    walletBackupItem(account = restoredAccount, enabledWallets = listOf(backedUpWallet(usdtQueryId))),
                    walletBackupItem(account = secondAccount, enabledWallets = listOf(backedUpWallet(scamQueryId)))
                ),
                watchlist = listOf("bitcoin"),
                settings = minimalSettings(),
                contacts = listOf(Contact(uid = "contact-1", name = "Alice", addresses = emptyList()))
            ),
            passphrase = "passphrase"
        )

        assertTrue(outcome is RestoreOutcome.TokensNeedReview)
        coVerify(exactly = 0) { accountManager.import(any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
        verify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
        verify(exactly = 0) { marketFavoritesManager.addAll(any()) }
        verify(exactly = 0) { contactsRepository.restore(any<List<Contact>>()) }
        verify(exactly = 0) { balanceViewTypeManager.setViewType(any()) }
        verify(exactly = 0) { evmSyncSourceManager.saveSyncSource(any(), any(), any()) }
    }

    @Test
    fun restoreSingleWalletBackup_secondCallWithApproval_savesTokenWithFileMetadata() = runTest {
        val saved = captureRestoredWallets()
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns restoredAccount
        val backup = mockk<BackupLocalModule.WalletBackup> {
            every { manualBackup } returns true
            every { enabledWallets } returns listOf(
                backedUpWallet(manuallyAddedQueryId).copy(coinName = "My Token", coinCode = "MYT", decimals = 8)
            )
        }

        val firstOutcome = backupProvider.restoreSingleWalletBackup(
            restoredAccount.type, "Restored", backup, BackupSource.Legacy
        )
        assertTrue(firstOutcome is RestoreOutcome.TokensNeedReview)

        val secondOutcome = backupProvider.restoreSingleWalletBackup(
            restoredAccount.type, "Restored", backup, BackupSource.Legacy,
            approved = setOf(manuallyAddedQueryId)
        )

        assertEquals(RestoreOutcome.Restored, secondOutcome)
        val restored = saved.captured.single()
        assertEquals(manuallyAddedQueryId, restored.tokenQueryId)
        assertEquals("My Token", restored.coinName)
        assertEquals("MYT", restored.coinCode)
        assertEquals(8, restored.coinDecimals)
    }

    @Test
    fun restoreSingleWalletBackup_secondCallWithEmptyApproval_savesEverythingElseWithoutDeclinedToken() = runTest {
        curate(usdtQueryId)
        val saved = captureRestoredWallets()
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns restoredAccount
        val backup = mockk<BackupLocalModule.WalletBackup> {
            every { manualBackup } returns true
            every { enabledWallets } returns listOf(backedUpWallet(usdtQueryId), backedUpWallet(scamQueryId))
        }

        val firstOutcome = backupProvider.restoreSingleWalletBackup(
            restoredAccount.type, "Restored", backup, BackupSource.Legacy
        )
        assertTrue(firstOutcome is RestoreOutcome.TokensNeedReview)

        val secondOutcome = backupProvider.restoreSingleWalletBackup(
            restoredAccount.type, "Restored", backup, BackupSource.Legacy, approved = emptySet()
        )

        assertEquals(RestoreOutcome.Restored, secondOutcome)
        assertEquals(listOf(usdtQueryId), saved.captured.map { it.tokenQueryId })
    }

    @Test
    fun restoreSingleWalletBackup_secondCallAfterReview_createsExactlyOneAccount() = runTest {
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns restoredAccount
        val backup = mockk<BackupLocalModule.WalletBackup> {
            every { manualBackup } returns true
            every { enabledWallets } returns listOf(backedUpWallet(manuallyAddedQueryId))
        }

        backupProvider.restoreSingleWalletBackup(restoredAccount.type, "Restored", backup, BackupSource.Legacy)
        backupProvider.restoreSingleWalletBackup(
            restoredAccount.type, "Restored", backup, BackupSource.Legacy,
            approved = setOf(manuallyAddedQueryId)
        )

        verify(exactly = 1) { accountManager.save(any(), any()) }
    }

    @Test
    fun restoreSingleWalletBackup_authenticatedSourceManuallyAddedToken_returnsTokensNeedReviewAndSavesNothing() =
        runTest {
            val outcome = backupProvider.restoreSingleWalletBackup(
                walletBackupItem(
                    enabledWallets = listOf(backedUpWallet(manuallyAddedQueryId)),
                    source = BackupSource.Authenticated
                )
            )

            assertEquals(
                RestoreOutcome.TokensNeedReview(
                    listOf(
                        WalletDeclinedTokens(
                            restoredAccount.id,
                            "Restored",
                            listOf(DeclinedToken(manuallyAddedQueryId, "Tether", "USDT", decimals = 6))
                        )
                    )
                ),
                outcome
            )
        }

    @Test
    fun restoreSingleWalletBackup_authenticatedSourceManuallyAddedTokenApproved_restoresItFromFileMetadata() = runTest {
        val saved = captureRestoredWallets()

        val outcome = backupProvider.restoreSingleWalletBackup(
            walletBackupItem(
                enabledWallets = listOf(backedUpWallet(manuallyAddedQueryId)),
                source = BackupSource.Authenticated
            ),
            approved = setOf(manuallyAddedQueryId)
        )

        assertEquals(RestoreOutcome.Restored, outcome)
        val restored = saved.captured.single()
        assertEquals(manuallyAddedQueryId, restored.tokenQueryId)
        assertEquals("Tether", restored.coinName)
        assertEquals("USDT", restored.coinCode)
        assertEquals(6, restored.coinDecimals)
    }
}
