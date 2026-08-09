package cash.p.terminal.modules.restoreaccount.duplicatewallet

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `copyAccount` runs on `Dispatchers.IO`, so tests await its real completion signal. */
private const val SAVE_TIMEOUT_MS = 5_000L

/** Differs from the source account's passphrase, so it derives a different seed. */
private const val NEW_PASSPHRASE = "new-passphrase"

/** Source-side passphrase, for the cases where the source is not passphrase-less. */
private const val OLD_PASSPHRASE = "source-passphrase"

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicateWalletViewModelTest {

    private val accountManager: IAccountManager = mockk(relaxed = true)
    private val accountFactory: IAccountFactory = mockk(relaxed = true)
    private val moneroWalletUseCase: MoneroWalletUseCase = mockk(relaxed = true)
    private val enabledWalletStorage: IEnabledWalletStorage = mockk(relaxed = true)
    private val walletManager: IWalletManager = mockk(relaxed = true)
    private val restoreSettingsManager: RestoreSettingsManager = mockk(relaxed = true)
    private val localStorage: ILocalStorage = mockk(relaxed = true)
    private val marketKit: MarketKitWrapper = mockk(relaxed = true)

    private val usdtQuery = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xusdt"))
    private val scamQuery = TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Eip20("0xscam"))
    private val pythAddress = "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3"
    private val unsupportedQuery = TokenQuery(BlockchainType.Solana, TokenType.Spl(pythAddress))

    private val sourceWords = List(12) { "abandon" }

    private val accountToCopy = Account(
        id = "source-account-id",
        name = "Main",
        type = AccountType.Mnemonic(sourceWords, ""),
        origin = AccountOrigin.Restored,
        level = 0
    )

    private val newAccount = accountToCopy.copy(id = "new-account-id", name = "Main copy")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { accountFactory.getUniqueName(any(), any()) } answers { firstArg() }
        // Mirrors the requested type so tests can tell a same-identity copy from a different-identity one.
        every { accountFactory.account(any(), any(), any(), any(), any()) } answers {
            newAccount.copy(type = secondArg())
        }
        every { restoreSettingsManager.settings(any(), any()) } returns RestoreSettings()
        // Nothing is known to MarketKit unless a test says otherwise
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Matching source/destination addresses used to suppress the review; they no longer do.
    @Test
    fun createAccount_passphraseUnchanged_stillRaisesReviewForUnknownToken() {
        sourceWallets(enabledWallet(usdtQuery), enabledWallet(scamQuery))
        curate(usdtQuery)

        val viewModel = createViewModel()
        viewModel.createAccount()
        awaitUiState(viewModel) { it.tokenReview != null }

        val declined = requireNotNull(viewModel.uiState.tokenReview).wallets.single().tokens
        assertEquals(listOf(scamQuery.id), declined.map { it.tokenQueryId })
        verify(exactly = 0) { accountManager.save(any(), any()) }
    }

    @Test
    fun createAccount_passphraseAdded_copiesOnlyTokensKnownToMarketKit() {
        sourceWallets(enabledWallet(usdtQuery), enabledWallet(scamQuery))
        curate(usdtQuery)
        val saved = captureSavedWallets()

        val viewModel = createViewModel()
        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }
        viewModel.onApproveTokens(emptyMap())

        assertEquals(listOf(usdtQuery.id), awaitSaved(saved).map { it.tokenQueryId })
    }

    @Test
    fun createAccount_passphraseAddedAndNothingCurated_savesAccountWithoutWallets() {
        sourceWallets(enabledWallet(scamQuery))
        val saved = captureSavedWallets()

        val viewModel = createViewModel()
        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }
        viewModel.onApproveTokens(emptyMap())

        assertTrue(awaitSaved(saved).isEmpty())
        verify { accountManager.save(any(), any()) }
    }

    @Test
    fun createAccount_catalogResolvesNothing_raisesReviewInsteadOfCopying() {
        sourceWallets(enabledWallet(usdtQuery))

        val viewModel = createViewModel()
        viewModel.createAccount()
        awaitUiState(viewModel) { it.tokenReview != null }

        val declined = requireNotNull(viewModel.uiState.tokenReview).wallets.single().tokens
        assertEquals(listOf(usdtQuery.id), declined.map { it.tokenQueryId })
        verify(exactly = 0) { accountManager.save(any(), any()) }
    }

    @Test
    fun createAccount_trustedToken_carriesNewAccountIdAndCuratedMetadata() {
        sourceWallets(
            enabledWallet(usdtQuery).copy(
                coinName = "Free Airdrop",
                coinCode = "SCAM",
                coinDecimals = 18,
                coinImage = "scam-url"
            )
        )
        curate(usdtQuery)
        val saved = captureSavedWallets()

        createViewModel().createAccount()

        val copied = awaitSaved(saved).single()
        assertEquals(newAccount.id, copied.accountId)
        assertEquals("Tether", copied.coinName)
        assertEquals("USDT", copied.coinCode)
        assertEquals(6, copied.coinDecimals)
        assertNull(copied.coinImage)
    }

    @Test
    fun createAccount_curatedTokenTypeUnsupported_copiesItWithSourceDecimalsAndCuratedMetadata() {
        sourceWallets(spoofedUnsupportedSourceRow())
        curateUnsupportedPyth()
        val saved = captureSavedWallets()

        createViewModel().createAccount()

        val copied = awaitSaved(saved).single()
        assertEquals(unsupportedQuery.id, copied.tokenQueryId)
        assertEquals("Pyth Network", copied.coinName)
        assertEquals("PYTH", copied.coinCode)
        assertEquals(18, copied.coinDecimals)
    }

    @Test
    fun createAccount_passphraseAdded_copiesCuratedTokenTypeUnsupported() =
        assertUnsupportedRowCopied(sourcePassphrase = "", expectedPassphrase = NEW_PASSPHRASE) {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
        }

    @Test
    fun createAccount_passphraseReplaced_copiesCuratedTokenTypeUnsupported() =
        assertUnsupportedRowCopied(OLD_PASSPHRASE, expectedPassphrase = NEW_PASSPHRASE) {
            enterPassphrase(NEW_PASSPHRASE)
        }

    @Test
    fun createAccount_existingPassphraseDisabled_copiesCuratedTokenTypeUnsupported() =
        assertUnsupportedRowCopied(OLD_PASSPHRASE, expectedPassphrase = "") {
            onTogglePassphrase(false)
        }

    @Test
    fun createAccount_existingPassphraseUnchanged_copiesCuratedTokenTypeUnsupported() =
        assertUnsupportedRowCopied(OLD_PASSPHRASE, expectedPassphrase = OLD_PASSPHRASE) {}

    @Test
    fun createAccount_sameContractInTwoCases_copiesOneCuratedRow() {
        val checksummed = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xUSDT"))
        sourceWallets(enabledWallet(checksummed), enabledWallet(usdtQuery))
        // MarketKit's LIKE '%reference' fallback ignores ASCII case, so both forms resolve to the curated token.
        val curated = Token(
            coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
            blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
            type = usdtQuery.tokenType,
            decimals = 6
        )
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>()
                .filter { it.id.lowercase() == usdtQuery.id }
                .map { curated }
                .distinct()
        }
        val saved = captureSavedWallets()

        createViewModel().createAccount()

        assertEquals(listOf(usdtQuery.id), awaitSaved(saved).map { it.tokenQueryId })
    }

    @Test
    fun createAccount_unknownToken_doesNotCopyRestoreSettingsOfItsBlockchain() {
        sourceWallets(enabledWallet(usdtQuery), enabledWallet(scamQuery))
        curate(usdtQuery)
        val saved = captureSavedWallets()

        val viewModel = createViewModel()
        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }
        viewModel.onApproveTokens(emptyMap())
        awaitSaved(saved)

        verify { restoreSettingsManager.settings(accountToCopy, BlockchainType.Ethereum) }
        verify {
            restoreSettingsManager.save(
                any(),
                match { it.id == newAccount.id },
                BlockchainType.Ethereum
            )
        }
        verify(exactly = 0) {
            restoreSettingsManager.save(any(), any(), BlockchainType.BinanceSmartChain)
        }
    }

    @Test
    fun createAccount_curatedLookupFails_persistsNothing() {
        sourceWallets(enabledWallet(usdtQuery))
        every { marketKit.tokens(any<List<TokenQuery>>()) } throws IllegalStateException("coin catalog unavailable")

        val viewModel = createViewModel()
        viewModel.createAccount()
        awaitUiState(viewModel) { it.error != null }

        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
        assertEquals("coin catalog unavailable", viewModel.uiState.error)
        assertTrue(viewModel.uiState.createButtonEnabled)
    }

    // A post-commit failure test (saveEnabledWallets throwing after accountManager.save succeeds)
    // is intentionally omitted: copyAccount leaves that segment uncaught, and kotlinx-coroutines-test
    // reports the resulting uncaught exception via a JVM-wide handler, so it surfaces as a spurious
    // failure in an unrelated later test instead of this one (confirmed empirically).

    @Test
    fun createAccount_passphraseChangedWithManuallyAddedToken_exposesReviewAndWritesNothing() {
        sourceWallets(enabledWallet(scamQuery))
        val viewModel = createViewModel()

        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }

        assertFalse(viewModel.uiState.closeScreen)
        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
        verify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    @Test
    fun onDismissTokenReview_reviewExposed_writesNothingAndReenablesCreateButton() {
        sourceWallets(enabledWallet(scamQuery))
        val viewModel = createViewModel()
        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }

        viewModel.onDismissTokenReview()

        assertNull(viewModel.uiState.tokenReview)
        assertTrue(viewModel.uiState.createButtonEnabled)
        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun onApproveTokens_approveAll_savesEveryDeclinedRowAndCloses() {
        sourceWallets(enabledWallet(usdtQuery), enabledWallet(scamQuery))
        curate(usdtQuery)
        val saved = captureSavedWallets()

        val viewModel = createViewModel()
        viewModel.createAccount()
        awaitUiState(viewModel) { it.tokenReview != null }
        val review = requireNotNull(viewModel.uiState.tokenReview)

        viewModel.onApproveTokens(review.allTokenIds)

        val savedWallets = awaitSaved(saved)
        assertEquals(setOf(usdtQuery.id, scamQuery.id), savedWallets.map { it.tokenQueryId }.toSet())
        verify(exactly = 1) { accountManager.save(any(), any()) }
        assertTrue(viewModel.uiState.closeScreen)
    }

    // Skip-all is a non-null empty approval, not an abort: the `approved == null` guard in
    // `copyAccount` is bypassed, so the account is written with every catalog-resolved row.
    @Test
    fun onApproveTokens_skipAll_writesAccountWithCatalogRowsButWithoutDeclinedRow() {
        sourceWallets(enabledWallet(usdtQuery), enabledWallet(scamQuery))
        curate(usdtQuery)
        val saved = captureSavedWallets()

        val viewModel = createViewModel()
        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }

        viewModel.onApproveTokens(emptyMap())

        assertEquals(listOf(usdtQuery.id), awaitSaved(saved).map { it.tokenQueryId })
        verify(exactly = 1) { accountManager.save(any(), any()) }
    }

    // Also checks the created account's passphrase, so a false pass can't hide a silently-failed passphrase apply.
    private fun assertUnsupportedRowCopied(
        sourcePassphrase: String,
        expectedPassphrase: String,
        enterDestinationPassphrase: DuplicateWalletViewModel.() -> Unit
    ) {
        sourceWallets(spoofedUnsupportedSourceRow())
        curateUnsupportedPyth()
        val saved = captureSavedWallets()

        createViewModel(sourcePassphrase).apply {
            enterDestinationPassphrase()
            createAccount()
        }

        assertEquals(18, awaitSaved(saved).single().coinDecimals)
        val created = slot<Account>()
        verify { accountManager.save(capture(created), any()) }
        assertEquals(expectedPassphrase, (created.captured.type as AccountType.Mnemonic).passphrase)
    }

    private fun DuplicateWalletViewModel.enterPassphrase(value: String) {
        onChangePassphrase(value)
        onChangePassphraseConfirmation(value)
    }

    /** [sourcePassphrase] varies the source identity; the destination one is set through the UI. */
    private fun createViewModel(sourcePassphrase: String = "") = DuplicateWalletViewModel(
        accountToCopy = accountToCopy.copy(
            type = AccountType.Mnemonic(sourceWords, sourcePassphrase)
        ),
        accountManager = accountManager,
        accountFactory = accountFactory,
        moneroWalletUseCase = moneroWalletUseCase,
        enabledWalletStorage = enabledWalletStorage,
        walletManager = walletManager,
        restoreSettingsManager = restoreSettingsManager,
        localStorage = localStorage,
        marketKit = marketKit
    )

    private fun enabledWallet(query: TokenQuery) = EnabledWallet(
        tokenQueryId = query.id,
        accountId = accountToCopy.id,
        coinName = "Tether",
        coinCode = "USDT",
        coinDecimals = 6,
        coinImage = "image-url"
    )

    private fun sourceWallets(vararg wallets: EnabledWallet) {
        every { enabledWalletStorage.enabledWallets(accountToCopy.id) } returns wallets.toList()
    }

    /** Source row whose metadata is attacker-shaped: only its decimals may ever be carried over. */
    private fun spoofedUnsupportedSourceRow() = enabledWallet(unsupportedQuery).copy(
        coinName = "Spoofed",
        coinCode = "SPF",
        coinDecimals = 18
    )

    /** `CoinDao` maps a catalog row with null decimals to [TokenType.Unsupported] plus a zero. */
    private fun curateUnsupportedPyth() {
        val curated = Token(
            coin = Coin(uid = "pyth-network", name = "Pyth Network", code = "PYTH"),
            blockchain = Blockchain(BlockchainType.Solana, "Solana", null),
            type = TokenType.Unsupported("spl", pythAddress),
            decimals = 0
        )
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().filter { it.id == unsupportedQuery.id }.map { curated }
        }
    }

    /** Makes MarketKit resolve only [queries], mirroring the curated coin database. */
    private fun curate(vararg queries: TokenQuery) {
        val curated = queries.associateWith { query ->
            Token(
                coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
                blockchain = Blockchain(query.blockchainType, "Blockchain", null),
                type = query.tokenType,
                decimals = 6
            )
        }
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().mapNotNull { curated[it] }
        }
    }

    private fun captureSavedWallets(): CompletableDeferred<List<EnabledWallet>> {
        val saved = CompletableDeferred<List<EnabledWallet>>()
        coEvery { walletManager.saveEnabledWallets(any()) } answers { saved.complete(firstArg()) }
        return saved
    }

    private fun awaitSaved(saved: CompletableDeferred<List<EnabledWallet>>): List<EnabledWallet> =
        runBlocking { withTimeout(SAVE_TIMEOUT_MS) { saved.await() } }

    /** `uiState` is plain Compose state set from a background dispatcher, so tests poll for it. */
    private fun awaitUiState(
        viewModel: DuplicateWalletViewModel,
        until: (DuplicateWalletUiState) -> Boolean
    ) {
        runBlocking {
            withTimeout(SAVE_TIMEOUT_MS) {
                while (!until(viewModel.uiState)) {
                    delay(10)
                }
            }
        }
    }
}
