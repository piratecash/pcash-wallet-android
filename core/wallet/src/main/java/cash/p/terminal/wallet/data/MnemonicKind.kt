package cash.p.terminal.wallet.data

import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.R

enum class MnemonicKind(val wordsCount: Int) {
    Mnemonic12(12),
    Mnemonic15(15),
    Mnemonic18(18),
    Mnemonic21(21),
    Mnemonic24(24),
    Mnemonic25(25),
    Unknown(-1);

    val title = Translator.getString(
        R.string.CreateWallet_N_Words,
        wordsCount
    )

    val titleLong: String
        get() = when (this) {
            Mnemonic12 -> Translator.getString(
                R.string.CreateWallet_N_WordsRecommended,
                wordsCount
            )

            Mnemonic25 -> Translator.getString(
                R.string.CreateWallet_N_Words_monero,
                wordsCount
            )

            else -> title
        }

    companion object {
        fun getKind(wordLis: List<String>): MnemonicKind {
            return when (wordLis.size) {
                12 -> Mnemonic12
                15 -> Mnemonic15
                18 -> Mnemonic18
                21 -> Mnemonic21
                24 -> Mnemonic24
                25 -> Mnemonic25
                else -> Unknown
            }
        }
    }
}