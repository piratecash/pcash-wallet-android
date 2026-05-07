package cash.p.terminal.modules.restoreaccount.restoremnemonic

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.utils.Bip39LanguageDetector
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.core.usecase.ValidateMoneroMnemonicUseCase
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.IThirdKeyboard
import io.horizontalsystems.hdwalletkit.Language
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreMnemonicViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val validateMoneroMnemonicUseCase = mockk<ValidateMoneroMnemonicUseCase>()
    private val validateMoneroHeightUseCase = mockk<ValidateMoneroHeightUseCase>()
    private val moneroWalletUseCase = mockk<MoneroWalletUseCase>()
    private val accountManager = mockk<IAccountManager>()
    private val walletActivator = mockk<WalletActivator>()
    private val seedPhraseQrCrypto = mockk<SeedPhraseQrCrypto>()
    private val accountFactory = mockk<IAccountFactory>()
    private val thirdKeyboardStorage = mockk<IThirdKeyboard>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { accountFactory.getNextAccountName() } returns "Wallet 1"
        every { thirdKeyboardStorage.isThirdPartyKeyboardAllowed } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onToggleMoneroMnemonic_enabledThenDisabled_preservesBip39Language() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.setMnemonicLanguage(Language.Spanish)
            advanceUntilIdle()
            assertEquals(Language.Spanish, viewModel.uiState.language)

            viewModel.onToggleMoneroMnemonic(true)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.isMoneroMnemonic)
            assertEquals(Language.English, viewModel.uiState.language)

            viewModel.onToggleMoneroMnemonic(false)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.isMoneroMnemonic)
            assertEquals(Language.Spanish, viewModel.uiState.language)
        }

    @Test
    fun setMnemonicLanguage_moneroMode_ignoresLanguageChange() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.onToggleMoneroMnemonic(true)
            viewModel.setMnemonicLanguage(Language.Japanese)
            advanceUntilIdle()

            assertEquals(Language.English, viewModel.uiState.language)

            viewModel.onToggleMoneroMnemonic(false)
            advanceUntilIdle()
            assertEquals(Language.English, viewModel.uiState.language)
        }

    @Test
    fun applyMnemonicPhrase_moneroWords_preservesBip39Language() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.setMnemonicLanguage(Language.French)
            viewModel.applyMnemonicPhrase(
                words = List(25) { "word$it" },
                passphrase = "",
                moneroHeight = 123L,
                language = null
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.isMoneroMnemonic)
            assertEquals(Language.English, viewModel.uiState.language)
            assertEquals("123", viewModel.uiState.height)

            viewModel.onToggleMoneroMnemonic(false)
            advanceUntilIdle()
            assertEquals(Language.French, viewModel.uiState.language)
        }

    // ==================== Language autodetect on user input (#6) ====================

    private val spanishSeed12 = List(11) { "ábaco" } + "abierto"
    private val japaneseSeed12 = List(11) { "あいこくしん" } + "あおぞら"
    private val simplifiedChineseSeed12 = List(11) { "的" } + "在"

    @Test
    fun onEnterMnemonicPhrase_pastesSpanishWords_autoSwitchesLanguage() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            val text = spanishSeed12.joinToString(" ")
            viewModel.onEnterMnemonicPhrase(text, text.length)
            advanceUntilIdle()

            assertEquals(
                Language.Spanish,
                viewModel.uiState.language,
                "Pasting full Spanish mnemonic must switch language"
            )
            assertTrue(
                viewModel.uiState.invalidWordRanges.isEmpty(),
                "All Spanish words must validate as a side-effect of the switch"
            )
        }

    @Test
    fun onEnterMnemonicPhrase_pastesJapaneseWords_autoSwitchesLanguage() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            val text = japaneseSeed12.joinToString(" ")
            viewModel.onEnterMnemonicPhrase(text, text.length)
            advanceUntilIdle()

            assertEquals(Language.Japanese, viewModel.uiState.language)
            assertTrue(viewModel.uiState.invalidWordRanges.isEmpty())
        }

    @Test
    fun onEnterMnemonicPhrase_pastesChineseWords_autoSwitchesLanguage() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            val text = simplifiedChineseSeed12.joinToString(" ")
            viewModel.onEnterMnemonicPhrase(text, text.length)
            advanceUntilIdle()

            assertEquals(Language.SimplifiedChinese, viewModel.uiState.language)
            assertTrue(viewModel.uiState.invalidWordRanges.isEmpty())
        }

    @Test
    fun onEnterMnemonicPhrase_pastesEnglishWords_keepsEnglishLanguage() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            val text = "abandon abandon abandon abandon abandon abandon " +
                    "abandon abandon abandon abandon abandon about"
            viewModel.onEnterMnemonicPhrase(text, text.length)
            advanceUntilIdle()

            assertEquals(Language.English, viewModel.uiState.language)
        }

    @Test
    fun onEnterMnemonicPhrase_singleWordOnly_doesNotChangeLanguage() =
        runTest(dispatcher) {
            // One word may be ambiguous across multiple wordlists — don't flip on partial input.
            val viewModel = createViewModel()
            viewModel.setMnemonicLanguage(Language.French)
            advanceUntilIdle()

            viewModel.onEnterMnemonicPhrase("ábaco", 5)
            advanceUntilIdle()

            assertEquals(
                Language.French,
                viewModel.uiState.language,
                "Single-word input must not auto-switch language"
            )
        }

    // ==================== Language hint from QR scan (#3) ====================

    @Test
    fun applyMnemonicPhrase_v2WithLanguage_setsBip39Language() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.applyMnemonicPhrase(
                words = spanishSeed12,
                passphrase = "",
                moneroHeight = null,
                language = Language.Spanish
            )
            advanceUntilIdle()

            assertEquals(Language.Spanish, viewModel.uiState.language)
            assertTrue(viewModel.uiState.invalidWordRanges.isEmpty())
        }

    @Test
    fun applyMnemonicPhrase_languageHintOverridesAutodetect_setsHintLanguage() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            assertEquals(
                Language.SimplifiedChinese,
                Bip39LanguageDetector.detectExact(simplifiedChineseSeed12).firstOrNull()
            )

            viewModel.applyMnemonicPhrase(
                words = simplifiedChineseSeed12,
                passphrase = "",
                moneroHeight = null,
                language = Language.TraditionalChinese
            )
            advanceUntilIdle()

            assertEquals(Language.TraditionalChinese, viewModel.uiState.language)
            assertTrue(viewModel.uiState.invalidWordRanges.isEmpty())
        }

    @Test
    fun handleScannedQrData_decryptedHasLanguage_forwardsLanguageInResult() {
        val viewModel = createViewModel()

        every { seedPhraseQrCrypto.decrypt("seed:abc") } returns Result.success(
            SeedPhraseQrCrypto.DecryptedSeed(
                words = spanishSeed12,
                passphrase = "",
                height = null,
                language = Language.Spanish
            )
        )

        val result = viewModel.handleScannedQrData("seed:abc")
            as RestoreMnemonicModule.QrScanResult.Success

        assertEquals(
            Language.Spanish,
            result.language,
            "Consumer must forward decrypted.language; null breaks the JSON v2 hint contract"
        )
    }

    @Test
    fun handleScannedQrData_legacyDecryptedNullLanguage_returnsNullLanguage() {
        val viewModel = createViewModel()

        every { seedPhraseQrCrypto.decrypt("seed:legacy") } returns Result.success(
            SeedPhraseQrCrypto.DecryptedSeed(
                words = japaneseSeed12,
                passphrase = "",
                height = null,
                language = null
            )
        )

        val result = viewModel.handleScannedQrData("seed:legacy")
            as RestoreMnemonicModule.QrScanResult.Success

        assertEquals(null, result.language)
    }

    @Test
    fun applyMnemonicPhrase_legacyWithoutLanguage_fallsBackToAutodetect() =
        runTest(dispatcher) {
            // Legacy QR (no language field) must still produce correct UI by autodetecting
            // from the words themselves.
            val viewModel = createViewModel()

            viewModel.applyMnemonicPhrase(
                words = japaneseSeed12,
                passphrase = "",
                moneroHeight = null,
                language = null
            )
            advanceUntilIdle()

            assertEquals(Language.Japanese, viewModel.uiState.language)
            assertTrue(viewModel.uiState.invalidWordRanges.isEmpty())
        }

    private fun createViewModel() = RestoreMnemonicViewModel(
        validateMoneroMnemonicUseCase = validateMoneroMnemonicUseCase,
        validateMoneroHeightUseCase = validateMoneroHeightUseCase,
        moneroWalletUseCase = moneroWalletUseCase,
        accountManager = accountManager,
        walletActivator = walletActivator,
        seedPhraseQrCrypto = seedPhraseQrCrypto,
        accountFactory = accountFactory,
        thirdKeyboardStorage = thirdKeyboardStorage
    )
}
