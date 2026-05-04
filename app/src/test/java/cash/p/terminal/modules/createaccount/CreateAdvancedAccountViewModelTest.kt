package cash.p.terminal.modules.createaccount

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.core.providers.PredefinedBlockchainSettingsProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.data.MnemonicKind
import io.horizontalsystems.hdwalletkit.Language
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateAdvancedAccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accountFactory = mockk<IAccountFactory>()
    private val wordsManager = mockk<WordsManager>()
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val walletActivator = mockk<WalletActivator>()
    private val passphraseValidator = mockk<PassphraseValidator>(relaxed = true)
    private val predefinedBlockchainSettingsProvider =
        mockk<PredefinedBlockchainSettingsProvider>(relaxed = true)
    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { accountFactory.getNextAccountName() } returns "Wallet 1"
        every { accountFactory.account(any(), any(), any(), any(), any()) } answers {
            account(secondArg())
        }
        every { localStorage.passphraseTermsAgreed } returns false
        coEvery { walletActivator.activateWalletsSuspended(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createMnemonicAccount_spanishLanguage_generatesSeedWithSelectedLanguage() =
        runTest(dispatcher) {
            val words = listOf(
                "fresa",
                "miope",
                "triste",
                "bozal",
                "etica",
                "risa",
                "virgo",
                "nariz",
                "grafico",
                "regla",
                "selva",
                "uva"
            )
            every { wordsManager.generateWords(12, Language.Spanish) } returns words
            val viewModel = createViewModel()

            viewModel.setMnemonicLanguage(Language.Spanish)
            viewModel.createMnemonicAccount()
            advanceUntilIdle()

            val mnemonic = assertIs<AccountType.Mnemonic>(viewModel.success)
            assertEquals(words, mnemonic.words)
            verify(exactly = 1) { wordsManager.generateWords(12, Language.Spanish) }
        }

    @Test
    fun shouldConfirmNonEnglishMnemonic_englishLanguage_returnsFalse() {
        val viewModel = createViewModel()

        assertFalse(viewModel.shouldConfirmNonEnglishMnemonic())
    }

    @Test
    fun setMnemonicKind_moneroKindDisplaysEnglish_preservesSelectedLanguage() {
        val viewModel = createViewModel()

        viewModel.setMnemonicLanguage(Language.Japanese)
        assertTrue(viewModel.shouldConfirmNonEnglishMnemonic())

        viewModel.setMnemonicKind(MnemonicKind.Mnemonic25)

        assertEquals(Language.Japanese, viewModel.selectedLanguage)
        assertEquals(Language.English, viewModel.displayedLanguage)
        assertFalse(viewModel.languageSelectionEnabled)
        assertFalse(viewModel.shouldConfirmNonEnglishMnemonic())
    }

    @Test
    fun setMnemonicKind_moneroThenBip39_restoresSelectedLanguage() {
        val viewModel = createViewModel()

        viewModel.setMnemonicLanguage(Language.Spanish)
        viewModel.setMnemonicKind(MnemonicKind.Mnemonic25)
        viewModel.setMnemonicKind(MnemonicKind.Mnemonic12)

        assertEquals(Language.Spanish, viewModel.selectedLanguage)
        assertEquals(Language.Spanish, viewModel.displayedLanguage)
        assertTrue(viewModel.shouldConfirmNonEnglishMnemonic())
    }

    @Test
    fun setMnemonicLanguage_moneroKind_ignoresLanguageChange() {
        val viewModel = createViewModel()

        viewModel.setMnemonicKind(MnemonicKind.Mnemonic25)
        viewModel.setMnemonicLanguage(Language.Spanish)

        assertEquals(Language.English, viewModel.selectedLanguage)
        assertEquals(Language.English, viewModel.displayedLanguage)
    }

    private fun createViewModel() = CreateAdvancedAccountViewModel(
        accountFactory = accountFactory,
        wordsManager = wordsManager,
        accountManager = accountManager,
        walletActivator = walletActivator,
        passphraseValidator = passphraseValidator,
        predefinedBlockchainSettingsProvider = predefinedBlockchainSettingsProvider,
        restoreSettingsManager = restoreSettingsManager,
        localStorage = localStorage
    )

    private fun account(type: AccountType): Account {
        return mockk {
            every { this@mockk.type } returns type
        }
    }
}
