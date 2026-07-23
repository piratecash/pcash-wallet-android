package cash.p.terminal.modules.softwareupdate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangelogSnippetParserTest {

    @Test
    fun parseLatest_currentFormat_countsLatestSectionOnly() {
        val markdown = """
            ## 🚀 Version 0.58.0 Update
            ### ✨ Improvements
            - a
            - b
            - c
            ### 🐛 Fixes
            - x
            - y
            ## 🚀 Version 0.57.2 Update
            ### 🐛 Fixes
            - old
        """.trimIndent()

        assertEquals(ChangelogSnippet(improvements = 3, fixes = 2), ChangelogSnippetParser.parseLatest(markdown))
    }

    @Test
    fun parseLatest_legacyFormatHeadingVariants_classifiesFixesByKeyword() {
        val markdown = """
            ## Main changes in version 0.46.2
            ### ✨ New Features
            - a
            ### 🎛 Interface Improvements
            - b
            ### 🛠 Important Fixes
            - c
            - d
        """.trimIndent()

        assertEquals(ChangelogSnippet(improvements = 2, fixes = 2), ChangelogSnippetParser.parseLatest(markdown))
    }

    @Test
    fun parseLatest_russianFixHeading_classifiedAsFixes() {
        val markdown = """
            ## 🚀 Версия 0.58.0
            ### ✨ Улучшения
            - a
            ### 🐛 Исправления
            - b
            - c
        """.trimIndent()

        assertEquals(ChangelogSnippet(improvements = 1, fixes = 2), ChangelogSnippetParser.parseLatest(markdown))
    }

    @Test
    fun parseLatest_noCountableContent_returnsNull() {
        assertNull(ChangelogSnippetParser.parseLatest("## Version 1.0.0\nJust prose, no lists"))
        assertNull(ChangelogSnippetParser.parseLatest(""))
        assertNull(ChangelogSnippetParser.parseLatest(null))
    }
}
