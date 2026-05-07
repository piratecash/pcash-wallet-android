package cash.p.terminal.core.usecase

import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.wallet.normalizeNFKD
import io.horizontalsystems.hdwalletkit.Mnemonic
import org.junit.Test
import kotlin.test.assertFailsWith

class ValidateMoneroMnemonicUseCaseTest {

    private val useCase = ValidateMoneroMnemonicUseCase(WordsManager(Mnemonic()))

    @Test
    fun invoke_bip39SpanishSeed_validatesMnemonic() {
        useCase(spanishWords(), isMonero = false)
    }

    @Test
    fun invoke_moneroEnglishSeed_validatesMoneroChecksum() {
        useCase(moneroWords(), isMonero = true)
    }

    @Test
    fun invoke_moneroSeedInvalidChecksum_throwsException() {
        val invalidWords = moneroWords().dropLast(1) + "abandon"

        assertFailsWith<IllegalArgumentException> {
            useCase(invalidWords, isMonero = true)
        }
    }

    private fun spanishWords() = listOf(
        "fresa",
        "miope",
        "triste",
        "bozal",
        "ética",
        "risa",
        "virgo",
        "nariz",
        "gráfico",
        "regla",
        "selva",
        "uva",
        "olivo",
        "candil",
        "servir"
    ).map { it.normalizeNFKD() }

    private fun moneroWords() = listOf(
        "tasked",
        "eight",
        "afraid",
        "laboratory",
        "tail",
        "feline",
        "rift",
        "reinvest",
        "vane",
        "cafe",
        "bailed",
        "foggy",
        "dormant",
        "paper",
        "jigsaw",
        "king",
        "hazard",
        "suture",
        "king",
        "dapper",
        "dummy",
        "jolted",
        "dating",
        "dwindling",
        "king"
    )
}
