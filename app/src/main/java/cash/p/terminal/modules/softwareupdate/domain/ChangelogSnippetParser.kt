package cash.p.terminal.modules.softwareupdate.domain

/**
 * Counts improvement vs fix bullet points in the latest (topmost) version section of a changelog
 * markdown file. Version sections are H2 headings carrying a semver (`## 🚀 Version X.Y.Z Update`
 * or `## Main changes in version X.Y.Z`), so the split is by the version number, not an English phrase.
 * Returns null when nothing countable is found, so the UI can fall back to a generic message.
 */
object ChangelogSnippetParser {

    private val VERSION_HEADING = Regex("""^##\s+.*\d+\.\d+(\.\d+)?""")
    private val SUBSECTION_HEADING = Regex("""^###\s+""")
    private val LIST_ITEM = Regex("""^\s*[-*]\s+\S""")
    private val FIX_KEYWORDS = listOf("fix", "исправ")

    fun parseLatest(markdown: String?): ChangelogSnippet? {
        if (markdown.isNullOrBlank()) return null
        val section = latestVersionSection(markdown) ?: return null
        var improvements = 0
        var fixes = 0
        var inFixes = false
        for (rawLine in section) {
            val line = rawLine.trim()
            when {
                SUBSECTION_HEADING.containsMatchIn(line) ->
                    inFixes = FIX_KEYWORDS.any { line.lowercase().contains(it) }

                LIST_ITEM.containsMatchIn(rawLine) ->
                    if (inFixes) fixes++ else improvements++
            }
        }
        return if (improvements == 0 && fixes == 0) null else ChangelogSnippet(improvements, fixes)
    }

    private fun latestVersionSection(markdown: String): List<String>? {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { VERSION_HEADING.containsMatchIn(it) }
        if (start < 0) return null
        val afterStart = lines.drop(start + 1)
        val next = afterStart.indexOfFirst { VERSION_HEADING.containsMatchIn(it) }
        val end = if (next < 0) lines.size else start + 1 + next
        return lines.subList(start, end)
    }
}
