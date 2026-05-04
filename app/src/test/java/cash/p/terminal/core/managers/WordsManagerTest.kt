package cash.p.terminal.core.managers

import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.hdwalletkit.WordList
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordsManagerTest {

    private val mnemonic = Mnemonic()
    private val wordsManager = WordsManager(mnemonic)

    @Test
    fun generateWords_englishLanguage_generatesEnglishMnemonic() {
        val words = wordsManager.generateWords(12, Language.English)

        assertEquals(12, words.size)
        assertTrue(WordList.wordListStrict(Language.English).validWords(words))
        mnemonic.validateStrict(words)
    }

    @Test
    fun generateWords_japaneseLanguage_generatesValidJapaneseMnemonic() {
        val words = wordsManager.generateWords(12, Language.Japanese)

        assertEquals(12, words.size)
        assertTrue(WordList.wordListStrict(Language.Japanese).validWords(words))
        mnemonic.validateStrict(words)
    }
}
