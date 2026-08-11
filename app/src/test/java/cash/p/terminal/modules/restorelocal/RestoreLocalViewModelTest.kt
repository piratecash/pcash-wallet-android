package cash.p.terminal.modules.restorelocal

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.DeclinedToken
import cash.p.terminal.modules.backuplocal.BackupLocalModule
import cash.p.terminal.modules.backuplocal.fullbackup.BackupProvider
import cash.p.terminal.modules.backuplocal.fullbackup.BackupSource
import cash.p.terminal.modules.backuplocal.fullbackup.BackupViewItemFactory
import cash.p.terminal.modules.backuplocal.fullbackup.DecryptedFullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.RestoreOutcome
import cash.p.terminal.modules.backuplocal.fullbackup.WalletBackupItem
import cash.p.terminal.modules.backuplocal.fullbackup.WalletDeclinedTokens
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreLocalViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher))
    private val accountFactory = mockk<IAccountFactory> {
        every { getNextAccountName() } returns "Wallet 1"
        every { getUniqueName(any()) } answers { firstArg() }
    }
    private val backupProvider = mockk<BackupProvider>(relaxed = true) {
        every { parseV3Backup(any()) } returns null
    }
    private val backupViewItemFactory = mockk<BackupViewItemFactory>(relaxed = true)

    private fun createTempFile(content: ByteArray): File {
        val file = File.createTempFile("test_backup", ".tmp")
        file.writeBytes(content)
        return file
    }

    private fun binaryV4File(): File {
        val binaryData = ByteArray(10).apply {
            System.arraycopy(BackupLocalModule.BackupV4Binary.MAGIC, 0, this, 0, 4)
            this[4] = BackupLocalModule.BackupV4Binary.VERSION
        }
        return createTempFile(binaryData)
    }

    /** A V4-binary backup with exactly one wallet, so [RestoreLocalViewModel] treats it as single-wallet. */
    private fun walletBackupItem(accountId: String = "acct-1", tokenQueryId: String = "eth|native") = WalletBackupItem(
        account = Account(
            id = accountId,
            name = "Wallet 1",
            type = mockk<AccountType>(),
            origin = AccountOrigin.Restored,
            level = 0,
        ),
        enabledWallets = listOf(BackupLocalModule.EnabledWalletBackup(tokenQueryId = tokenQueryId, settings = null)),
        source = BackupSource.Legacy,
    )

    private fun singleWalletDecryptedBackup(walletItem: WalletBackupItem) = DecryptedFullBackup(
        wallets = listOf(walletItem),
        watchlist = emptyList(),
        settings = null,
        contacts = emptyList(),
    )

    private fun createViewModel(filePath: String?, fileName: String? = null) =
        RestoreLocalViewModel(
            backupFilePath = filePath,
            accountFactory = accountFactory,
            backupProvider = backupProvider,
            backupViewItemFactory = backupViewItemFactory,
            dispatcherProvider = dispatcherProvider,
            fileName = fileName
        )

    @Test
    fun init_binaryBackupFile_setsBackupV4Binary() = runTest(dispatcher) {
        val file = binaryV4File()

        val viewModel = createViewModel(file.absolutePath)
        advanceUntilIdle()

        assertNull(viewModel.uiState.parseError)
    }

    @Test
    fun init_jsonBackupFile_parsesAsJson() = runTest(dispatcher) {
        val json = """{"version":1}"""
        val file = createTempFile(json.toByteArray(Charsets.UTF_8))

        val viewModel = createViewModel(file.absolutePath)
        advanceUntilIdle()

        assertNull(viewModel.uiState.parseError)
    }

    @Test
    fun init_validFile_deletesFileAfterReading() = runTest(dispatcher) {
        val file = createTempFile("test".toByteArray())
        assertTrue(file.exists())

        createViewModel(file.absolutePath)
        advanceUntilIdle()

        assertTrue(!file.exists())
    }

    @Test
    fun init_missingFile_setsParseError() = runTest(dispatcher) {
        val viewModel = createViewModel("/tmp/nonexistent_backup_${System.nanoTime()}.tmp")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.parseError)
    }

    @Test
    fun init_nullFilePath_noError() = runTest(dispatcher) {
        val viewModel = createViewModel(null)
        advanceUntilIdle()

        assertNull(viewModel.uiState.parseError)
    }

    @Test
    fun onImportClick_singleWalletJson_restoresAsSingleWallet() = runTest(dispatcher) {
        val json = requireNotNull(
            requireNotNull(javaClass.classLoader).getResource("backup/wallet_backup_v2_sample.json")
        ).readText()
        val file = createTempFile(json.toByteArray(Charsets.UTF_8))

        val mockAccountType = mockk<AccountType>()
        coEvery { backupProvider.accountType(any(), any()) } returns mockAccountType
        coEvery {
            backupProvider.restoreSingleWalletBackup(mockAccountType, any(), any(), BackupSource.Legacy)
        } returns RestoreOutcome.Restored

        val viewModel = createViewModel(file.absolutePath)
        advanceUntilIdle()

        viewModel.onChangePassphrase("1")
        viewModel.onImportClick()
        advanceUntilIdle()

        // Should restore as single wallet, not as full backup
        assertNull("parseError should be null: ${viewModel.uiState.parseError}", viewModel.uiState.parseError)
        assertTrue("expected restored=true", viewModel.uiState.restored)
        assertFalse("expected showBackupItems=false", viewModel.uiState.showBackupItems)
        // Explicitly Legacy, not any(): a raw v2 envelope's enabled_wallets sit outside the MAC.
        coVerify {
            backupProvider.restoreSingleWalletBackup(
                mockAccountType, any(), any(), BackupSource.Legacy
            )
        }
    }

    @Test
    fun onImportClick_fullBackupJson_showsBackupItems() = runTest(dispatcher) {
        val json = requireNotNull(
            requireNotNull(javaClass.classLoader).getResource("backup/full_backup_v2_sample.json")
        ).readText()
        val file = createTempFile(json.toByteArray(Charsets.UTF_8))

        val decryptedFullBackup = mockk<DecryptedFullBackup>()
        // Explicitly Legacy, not any(): a raw JSON envelope leaves enabled_wallets outside the MAC.
        coEvery {
            backupProvider.decryptedFullBackup(any(), any(), BackupSource.Legacy)
        } returns decryptedFullBackup
        coEvery { backupProvider.fullBackupItems(decryptedFullBackup) } returns mockk(relaxed = true)
        every { backupViewItemFactory.backupViewItems(any()) } returns Pair(emptyList(), emptyList())

        val viewModel = createViewModel(file.absolutePath)
        advanceUntilIdle()

        viewModel.onChangePassphrase("1")
        viewModel.onImportClick()
        advanceUntilIdle()

        // Should show backup items screen, not restore as single wallet
        assertNull("parseError should be null: ${viewModel.uiState.parseError}", viewModel.uiState.parseError)
        assertTrue("expected showBackupItems=true", viewModel.uiState.showBackupItems)
        assertFalse("expected restored=false", viewModel.uiState.restored)
        coVerify(exactly = 0) {
            backupProvider.restoreSingleWalletBackup(any(), any(), any(), any())
        }
    }

    @Test
    fun onImportClick_v3FullBackup_declaresAuthenticatedSource() = runTest(dispatcher) {
        val file = createTempFile("""{"version":3,"encrypted":"x"}""".toByteArray())
        every { backupProvider.parseV3Backup(any()) } returns mockk()
        every { backupProvider.unwrapV3FormatWithKey(any(), any()) } returns
                Triple("""{"version":2,"id":"fb","timestamp":0,"wallets":[]}""", ByteArray(32), mockk())
        every { backupViewItemFactory.backupViewItems(any()) } returns Pair(emptyList(), emptyList())

        val viewModel = createViewModel(file.absolutePath)
        advanceUntilIdle()

        viewModel.onChangePassphrase("1")
        viewModel.onImportClick()
        advanceUntilIdle()

        assertNull("parseError should be null: ${viewModel.uiState.parseError}", viewModel.uiState.parseError)
        coVerify {
            backupProvider.decryptedFullBackupWithKey(
                any(), any(), any(), any(), BackupSource.Authenticated
            )
        }
    }

    /** The single-wallet branch reads the same verified ciphertext, so it is equally authentic. */
    @Test
    fun onImportClick_v3SingleWallet_declaresAuthenticatedSource() = runTest(dispatcher) {
        val file = createTempFile("""{"version":3,"encrypted":"x"}""".toByteArray())
        val innerJson = """
            {"crypto":{},"id":"i","type":"mnemonic","manual_backup":true,"file_backup":false,
             "timestamp":0,"version":2,
             "enabled_wallets":[{"token_query_id":"ethereum|native"}]}
        """.trimIndent()
        every { backupProvider.parseV3Backup(any()) } returns mockk()
        every { backupProvider.unwrapV3FormatWithKey(any(), any()) } returns
                Triple(innerJson, ByteArray(32), mockk())
        coEvery {
            backupProvider.accountTypeWithKey(any(), any(), any(), any())
        } returns mockk<AccountType>()
        coEvery {
            backupProvider.restoreSingleWalletBackup(any(), any(), any(), BackupSource.Authenticated)
        } returns RestoreOutcome.Restored

        val viewModel = createViewModel(file.absolutePath)
        advanceUntilIdle()

        viewModel.onChangePassphrase("1")
        viewModel.onImportClick()
        advanceUntilIdle()

        assertTrue("expected restored=true", viewModel.uiState.restored)
        coVerify {
            backupProvider.restoreSingleWalletBackup(
                any(), any(), any(), BackupSource.Authenticated
            )
        }
    }

    @Test
    fun onApproveTokens_v3SingleWalletReviewed_restoresViaSingleWalletPathAndReachesRestored() = runTest(dispatcher) {
        val file = createTempFile("""{"version":3,"encrypted":"x"}""".toByteArray())
        val innerJson = """
            {"crypto":{},"id":"i","type":"mnemonic","manual_backup":true,"file_backup":false,
             "timestamp":0,"version":2,
             "enabled_wallets":[{"token_query_id":"eth|native"}]}
        """.trimIndent()
        every { backupProvider.parseV3Backup(any()) } returns mockk()
        every { backupProvider.unwrapV3FormatWithKey(any(), any()) } returns
                Triple(innerJson, ByteArray(32), mockk())
        val mockAccountType = mockk<AccountType>()
        coEvery { backupProvider.accountTypeWithKey(any(), any(), any(), any()) } returns mockAccountType
        coEvery { backupProvider.accountType(any(), any()) } returns mockAccountType
        coEvery {
            backupProvider.restoreSingleWalletBackup(mockAccountType, any(), any(), BackupSource.Authenticated)
        } returns RestoreOutcome.TokensNeedReview(listOf(declinedWallet))
        val approvals = mapOf(declinedWallet.accountId to setOf("eth|native"))
        coEvery {
            backupProvider.restoreSingleWalletBackup(
                mockAccountType, any(), any(), BackupSource.Authenticated, setOf("eth|native")
            )
        } returns RestoreOutcome.Restored

        val viewModel = createViewModel(file.absolutePath)
        advanceUntilIdle()
        viewModel.onChangePassphrase("1")
        viewModel.onImportClick()
        advanceUntilIdle()
        assertNotNull("expected tokenReview to be set", viewModel.uiState.tokenReview)

        viewModel.onApproveTokens(approvals)
        advanceUntilIdle()

        assertTrue("expected restored=true", viewModel.uiState.restored)
        assertNull("sheet must not reopen", viewModel.uiState.tokenReview)
        coVerify(exactly = 1) {
            backupProvider.restoreSingleWalletBackup(
                mockAccountType, any(), any(), BackupSource.Authenticated, setOf("eth|native")
            )
        }
        coVerify(exactly = 0) { backupProvider.restoreFullBackup(any(), any(), any()) }
    }

    @Test
    fun onApproveTokens_v4BinarySingleWalletReviewed_restoresViaFullBackupPathAndReachesRestored() =
        runTest(dispatcher) {
            val file = binaryV4File()
            val walletItem = walletBackupItem()
            val decrypted = singleWalletDecryptedBackup(walletItem)
            coEvery { backupProvider.restoreFromV4BinaryBackup(any(), any()) } returns decrypted
            coEvery { backupProvider.restoreSingleWalletBackup(walletItem) } returns
                    RestoreOutcome.TokensNeedReview(listOf(declinedWallet))
            val approvals = mapOf(declinedWallet.accountId to setOf("eth|native"))
            coEvery { backupProvider.restoreFullBackup(decrypted, any(), approvals) } returns RestoreOutcome.Restored

            val viewModel = createViewModel(file.absolutePath)
            advanceUntilIdle()
            viewModel.onImportClick()
            advanceUntilIdle()
            assertNotNull("expected tokenReview to be set", viewModel.uiState.tokenReview)

            viewModel.onApproveTokens(approvals)
            advanceUntilIdle()

            assertTrue("expected restored=true", viewModel.uiState.restored)
            assertNull("sheet must not reopen", viewModel.uiState.tokenReview)
            coVerify(exactly = 1) { backupProvider.restoreFullBackup(decrypted, any(), approvals) }
        }

    private val declinedWallet = WalletDeclinedTokens(
        accountId = "acct-1",
        accountName = "Wallet 1",
        tokens = listOf(DeclinedToken(tokenQueryId = "eth|native", coinName = "Token", coinCode = "TK")),
    )

    /**
     * Drives onImportClick + restoreFullBackup() to the point where the provider's probing call
     * declines tokens for [declinedWallets], leaving [RestoreLocalViewModel.uiState.tokenReview] set
     * and nothing restored.
     */
    private fun TestScope.viewModelWithPendingFullBackupReview(
        declinedWallets: List<WalletDeclinedTokens> = listOf(declinedWallet)
    ): Pair<RestoreLocalViewModel, DecryptedFullBackup> {
        val json = requireNotNull(
            requireNotNull(javaClass.classLoader).getResource("backup/full_backup_v2_sample.json")
        ).readText()
        val file = createTempFile(json.toByteArray(Charsets.UTF_8))
        val decryptedFullBackup = mockk<DecryptedFullBackup>()

        coEvery {
            backupProvider.decryptedFullBackup(any(), any(), BackupSource.Legacy)
        } returns decryptedFullBackup
        coEvery { backupProvider.fullBackupItems(decryptedFullBackup) } returns mockk(relaxed = true)
        every { backupViewItemFactory.backupViewItems(any()) } returns Pair(emptyList(), emptyList())
        coEvery {
            backupProvider.restoreFullBackup(decryptedFullBackup, any(), null)
        } returns RestoreOutcome.TokensNeedReview(declinedWallets)

        val viewModel = createViewModel(file.absolutePath)
        advanceUntilIdle()
        viewModel.onChangePassphrase("1")
        viewModel.onImportClick()
        advanceUntilIdle()
        viewModel.restoreFullBackup()
        advanceUntilIdle()

        return viewModel to decryptedFullBackup
    }

    @Test
    fun restoreFullBackup_legacyBackupDeclinesTokens_exposesTokenReviewWithoutRestoring() = runTest(dispatcher) {
        val (viewModel, _) = viewModelWithPendingFullBackupReview()

        assertFalse("expected restored=false", viewModel.uiState.restored)
        assertNotNull("expected tokenReview to be set", viewModel.uiState.tokenReview)
    }

    @Test
    fun restoreFullBackup_multipleWalletsDeclineTokens_exposesOneReviewCarryingAllWallets() = runTest(dispatcher) {
        val secondDeclinedWallet = declinedWallet.copy(accountId = "acct-2", accountName = "Wallet 2")

        val (viewModel, _) = viewModelWithPendingFullBackupReview(listOf(declinedWallet, secondDeclinedWallet))

        val review = requireNotNull(viewModel.uiState.tokenReview)
        assertEquals(
            listOf(declinedWallet.accountId, secondDeclinedWallet.accountId), review.wallets.map { it.accountId })
    }

    @Test
    fun onDismissTokenReview_afterTokensNeedReview_restoresNothingAndClearsState() = runTest(dispatcher) {
        val (viewModel, decryptedFullBackup) = viewModelWithPendingFullBackupReview()

        viewModel.onDismissTokenReview()
        advanceUntilIdle()

        assertNull("expected tokenReview cleared", viewModel.uiState.tokenReview)
        assertFalse("expected showButtonSpinner=false", viewModel.uiState.showButtonSpinner)
        coVerify(exactly = 1) { backupProvider.restoreFullBackup(decryptedFullBackup, any(), any()) }
    }

    @Test
    fun onApproveTokens_finishesReview_restoresWithCollectedApprovals() = runTest(dispatcher) {
        val (viewModel, decryptedFullBackup) = viewModelWithPendingFullBackupReview()
        val expectedApprovals = mapOf(declinedWallet.accountId to setOf("eth|native"))
        coEvery {
            backupProvider.restoreFullBackup(decryptedFullBackup, any(), expectedApprovals)
        } returns RestoreOutcome.Restored

        viewModel.onReviewTokens()
        viewModel.onApproveTokens(expectedApprovals)
        advanceUntilIdle()

        assertTrue("expected restored=true", viewModel.uiState.restored)
        coVerify(exactly = 1) {
            backupProvider.restoreFullBackup(decryptedFullBackup, any(), expectedApprovals)
        }
    }

    // Skip-all is a non-null empty map, not an abort: it still completes the restore with every
    // catalog-resolved row, just without the declined one.
    @Test
    fun onApproveTokens_fullBackupSkipAll_restoresAndReachesRestored() = runTest(dispatcher) {
        val (viewModel, decryptedFullBackup) = viewModelWithPendingFullBackupReview()
        coEvery {
            backupProvider.restoreFullBackup(decryptedFullBackup, any(), emptyMap())
        } returns RestoreOutcome.Restored

        viewModel.onApproveTokens(emptyMap())
        advanceUntilIdle()

        assertTrue("expected restored=true", viewModel.uiState.restored)
        assertNull("sheet must not reopen", viewModel.uiState.tokenReview)
        coVerify(exactly = 1) { backupProvider.restoreFullBackup(decryptedFullBackup, any(), emptyMap()) }
    }

    @Test
    fun onApproveTokens_singleWalletSkipAll_restoresWithEmptyApprovalSetAndReachesRestored() = runTest(dispatcher) {
        val json = requireNotNull(
            requireNotNull(javaClass.classLoader).getResource("backup/wallet_backup_v2_sample.json")
        ).readText()
        val file = createTempFile(json.toByteArray(Charsets.UTF_8))

        val mockAccountType = mockk<AccountType>()
        coEvery { backupProvider.accountType(any(), any()) } returns mockAccountType
        coEvery {
            backupProvider.restoreSingleWalletBackup(mockAccountType, any(), any(), BackupSource.Legacy, null)
        } returns RestoreOutcome.TokensNeedReview(listOf(declinedWallet))
        coEvery {
            backupProvider.restoreSingleWalletBackup(mockAccountType, any(), any(), BackupSource.Legacy, emptySet())
        } returns RestoreOutcome.Restored

        val viewModel = createViewModel(file.absolutePath)
        advanceUntilIdle()
        viewModel.onChangePassphrase("1")
        viewModel.onImportClick()
        advanceUntilIdle()

        viewModel.onApproveTokens(emptyMap())
        advanceUntilIdle()

        assertTrue("expected restored=true", viewModel.uiState.restored)
        assertNull("sheet must not reopen", viewModel.uiState.tokenReview)
        coVerify(exactly = 1) {
            backupProvider.restoreSingleWalletBackup(mockAccountType, any(), any(), BackupSource.Legacy, emptySet())
        }
    }
}
