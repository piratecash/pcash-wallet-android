package cash.p.terminal.modules.manageaccount.recoveryphrase

import android.util.Base64
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.TimePasswordProvider
import cash.p.terminal.core.utils.MoneroWalletSeedConverter
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Language
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for RecoveryPhraseViewModel — locks down the QR encrypt path before
 * SeedPhraseQrCrypto JSON v2 migration. Existing producers must keep producing QRs that
 * round-trip with the new decoder.
 */
class RecoveryPhraseViewModelTest {

    private lateinit var crypto: SeedPhraseQrCrypto
    private lateinit var localStorage: ILocalStorage
    private lateinit var restoreSettingsManager: RestoreSettingsManager

    @Before
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }

        crypto = SeedPhraseQrCrypto(TimePasswordProvider())
        localStorage = mockk(relaxed = true)
        restoreSettingsManager = mockk()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private val bip39Words = ("abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about")
    private val moneroHeight = 2_500_000L
    private val moneroWords = (
        "tavern total bail plutonium faked faster beneath reinvest syndrome " +
                "dagger razor nobody acoustic tubes people germs myriad next " +
                "victim sipped oasis dagger razor acoustic acoustic"
        ).split(" ")
    private val moneroAccount = Account(
        id = "monero-id",
        name = "Monero",
        type = AccountType.MnemonicMonero(
            words = moneroWords,
            password = "",
            height = moneroHeight,
            walletInnerName = "wallet"
        ),
        origin = AccountOrigin.Created,
        level = 0
    )

    @Test
    fun init_bip39EnglishAccount_qrCarriesEnglishLanguageHint() {
        val account = mnemonicAccount(words = bip39Words.split(" "), passphrase = "")
        every { restoreSettingsManager.settings(any(), any()) } returns RestoreSettings()

        val viewModel = RecoveryPhraseViewModel(
            account = account,
            recoveryPhraseType = RecoveryPhraseFragment.RecoveryPhraseType.Mnemonic,
            seedPhraseQrCrypto = crypto,
            localStorage = localStorage,
            restoreSettingsManager = restoreSettingsManager
        )

        val decrypted = crypto.decrypt(awaitEncryptedContent(viewModel)).getOrNull()
            ?: error("Decrypt must succeed")

        assertEquals(
            "Producer must embed BIP39 language hint so consumer can skip autodetect",
            Language.English,
            decrypted.language
        )
    }

    @Test
    fun init_bip39SpanishAccount_qrCarriesSpanishLanguageHint() {
        val spanishWords = (List(11) { "ábaco" } + "abierto")
        val account = mnemonicAccount(words = spanishWords, passphrase = "")
        every { restoreSettingsManager.settings(any(), any()) } returns RestoreSettings()

        val viewModel = RecoveryPhraseViewModel(
            account = account,
            recoveryPhraseType = RecoveryPhraseFragment.RecoveryPhraseType.Mnemonic,
            seedPhraseQrCrypto = crypto,
            localStorage = localStorage,
            restoreSettingsManager = restoreSettingsManager
        )

        val decrypted = crypto.decrypt(awaitEncryptedContent(viewModel)).getOrNull()
            ?: error("Decrypt must succeed")

        assertEquals(Language.Spanish, decrypted.language)
        assertEquals(spanishWords, decrypted.words)
    }

    @Test
    fun init_moneroAccount_qrHasNoLanguageHint() {
        every { restoreSettingsManager.settings(any(), any()) } returns RestoreSettings()

        val viewModel = RecoveryPhraseViewModel(
            account = moneroAccount,
            recoveryPhraseType = RecoveryPhraseFragment.RecoveryPhraseType.Monero,
            seedPhraseQrCrypto = crypto,
            localStorage = localStorage,
            restoreSettingsManager = restoreSettingsManager
        )

        val decrypted = crypto.decrypt(awaitEncryptedContent(viewModel)).getOrNull()
            ?: error("Decrypt must succeed")

        assertNull(
            "Monero seeds use a separate wordlist — must not advertise a BIP39 language",
            decrypted.language
        )
    }

    @Test
    fun init_bip39MnemonicAccount_producesQrThatRoundTripsToSameWordsAndPassphrase() {
        val account = mnemonicAccount(words = bip39Words.split(" "), passphrase = "myPassphrase")
        every { restoreSettingsManager.settings(any(), any()) } returns RestoreSettings()

        val viewModel = RecoveryPhraseViewModel(
            account = account,
            recoveryPhraseType = RecoveryPhraseFragment.RecoveryPhraseType.Mnemonic,
            seedPhraseQrCrypto = crypto,
            localStorage = localStorage,
            restoreSettingsManager = restoreSettingsManager
        )

        val encrypted = awaitEncryptedContent(viewModel)
        assertTrue(
            "QR must start with seed: prefix",
            encrypted.startsWith(SeedPhraseQrCrypto.QR_PREFIX)
        )

        val decrypted = crypto.decrypt(encrypted).getOrNull()
            ?: error("Decrypt of regenerated QR must succeed")

        assertEquals(bip39Words.split(" "), decrypted.words)
        assertEquals("myPassphrase", decrypted.passphrase)
        assertNull("BIP39 account QR must not carry height", decrypted.height)
    }

    @Test
    fun init_bip39AccountWithoutPassphrase_producesQrWithEmptyPassphrase() {
        val account = mnemonicAccount(words = bip39Words.split(" "), passphrase = "")
        every { restoreSettingsManager.settings(any(), any()) } returns RestoreSettings()

        val viewModel = RecoveryPhraseViewModel(
            account = account,
            recoveryPhraseType = RecoveryPhraseFragment.RecoveryPhraseType.Mnemonic,
            seedPhraseQrCrypto = crypto,
            localStorage = localStorage,
            restoreSettingsManager = restoreSettingsManager
        )

        val decrypted = crypto.decrypt(awaitEncryptedContent(viewModel)).getOrNull()
            ?: error("Decrypt must succeed")

        assertEquals("", decrypted.passphrase)
        assertNull(decrypted.height)
    }

    @Test
    fun init_moneroOnlyAccount_producesQrWithHeightAnd25Words() {
        every { restoreSettingsManager.settings(any(), any()) } returns RestoreSettings()

        val viewModel = RecoveryPhraseViewModel(
            account = moneroAccount,
            recoveryPhraseType = RecoveryPhraseFragment.RecoveryPhraseType.Monero,
            seedPhraseQrCrypto = crypto,
            localStorage = localStorage,
            restoreSettingsManager = restoreSettingsManager
        )

        val decrypted = crypto.decrypt(awaitEncryptedContent(viewModel)).getOrNull()
            ?: error("Decrypt of Monero QR must succeed")

        assertEquals(25, decrypted.words.size)
        assertEquals(moneroHeight, decrypted.height)
    }

    @Test
    fun init_bip39AccountAsMoneroExport_convertsTo25WordsAndUsesRestoreSettingsHeight() {
        // RecoveryPhraseType.Monero from a BIP39 account = converts BIP39 -> Monero 25 words,
        // and pulls birthday height from RestoreSettings.
        val account = mnemonicAccount(words = bip39Words.split(" "), passphrase = "")
        val expectedMoneroWords = MoneroWalletSeedConverter.getLegacySeedFromBip39(
            words = bip39Words.split(" "),
            passphrase = ""
        )
        val settings = RestoreSettings().also { it.birthdayHeight = 1_234_567L }
        every {
            restoreSettingsManager.settings(account, BlockchainType.Monero)
        } returns settings

        val viewModel = RecoveryPhraseViewModel(
            account = account,
            recoveryPhraseType = RecoveryPhraseFragment.RecoveryPhraseType.Monero,
            seedPhraseQrCrypto = crypto,
            localStorage = localStorage,
            restoreSettingsManager = restoreSettingsManager
        )

        val decrypted = crypto.decrypt(awaitEncryptedContent(viewModel)).getOrNull()
            ?: error("Decrypt of converted Monero QR must succeed")

        assertEquals(expectedMoneroWords, decrypted.words)
        assertEquals(1_234_567L, decrypted.height)
    }

    @Test
    fun regenerateEncryptedQrContent_producesNewCiphertextButSamePlaintext() {
        val account = mnemonicAccount(words = bip39Words.split(" "), passphrase = "p")
        every { restoreSettingsManager.settings(any(), any()) } returns RestoreSettings()

        val viewModel = RecoveryPhraseViewModel(
            account = account,
            recoveryPhraseType = RecoveryPhraseFragment.RecoveryPhraseType.Mnemonic,
            seedPhraseQrCrypto = crypto,
            localStorage = localStorage,
            restoreSettingsManager = restoreSettingsManager
        )
        val first = awaitEncryptedContent(viewModel)

        viewModel.regenerateEncryptedQrContent()
        val second = awaitDifferentEncryptedContent(viewModel, previous = first)

        assertNotEquals(
            "Different IVs must produce different ciphertexts on regenerate",
            first,
            second
        )

        val firstDecoded = crypto.decrypt(first).getOrNull() ?: error("first decrypt")
        val secondDecoded = crypto.decrypt(second).getOrNull() ?: error("second decrypt")
        assertEquals(firstDecoded.words, secondDecoded.words)
        assertEquals(firstDecoded.passphrase, secondDecoded.passphrase)
    }

    private fun mnemonicAccount(words: List<String>, passphrase: String): Account = Account(
        id = "id-${words.size}-${passphrase.length}",
        name = "Test",
        type = AccountType.Mnemonic(words, passphrase),
        origin = AccountOrigin.Created,
        level = 0
    )

    private fun awaitEncryptedContent(viewModel: RecoveryPhraseViewModel): String =
        runBlocking {
            withTimeout(2_000) {
                while (viewModel.encryptedSeedQrContent.isEmpty()) {
                    delay(10)
                }
                viewModel.encryptedSeedQrContent
            }
        }

    private fun awaitDifferentEncryptedContent(
        viewModel: RecoveryPhraseViewModel,
        previous: String
    ): String = runBlocking {
        withTimeout(2_000) {
            while (viewModel.encryptedSeedQrContent == previous) {
                delay(10)
            }
            viewModel.encryptedSeedQrContent
        }
    }
}
