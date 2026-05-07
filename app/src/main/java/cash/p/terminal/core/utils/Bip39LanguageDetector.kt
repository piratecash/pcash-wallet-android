package cash.p.terminal.core.utils

import cash.p.terminal.wallet.normalizeNFKD
import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.WordList

/**
 * Exact-match BIP39 language detector. A language is a candidate only if EVERY input
 * word is present in its wordlist (full-match mode). Avoids the
 * [WordList.detectLanguages] quirk where Spanish/French strip all non-ASCII during
 * partial-match normalization, falsely matching CJK input.
 *
 * Caller may pass non-normalized words; this helper normalizes to NFKD internally.
 */
object Bip39LanguageDetector {
    fun detectExact(words: List<String>): List<Language> {
        if (words.isEmpty()) return emptyList()
        val normalized = words.map { it.normalizeNFKD() }
        return Language.entries.filter { lang ->
            val list = WordList.wordList(lang)
            normalized.all { list.validWord(it, false) }
        }
    }

    fun detectSingle(words: List<String>): Language? = detectExact(words).singleOrNull()
}
