package cash.p.terminal.network.github.data.repository

import cash.p.terminal.network.github.api.GithubApi
import cash.p.terminal.network.github.data.mapper.GithubReleaseMapper
import cash.p.terminal.network.github.domain.entity.AppRelease
import cash.p.terminal.network.github.domain.repository.AppUpdateRepository

internal class AppUpdateRepositoryImpl(
    private val githubApi: GithubApi,
    private val mapper: GithubReleaseMapper,
) : AppUpdateRepository {

    override suspend fun getLatestRelease(): AppRelease =
        mapper.map(githubApi.getLatestRelease())

    override suspend fun getVersionHistoryMinors(): List<String> =
        githubApi.getFolderContents("release-notes/$FALLBACK_LANGUAGE")
            .map { it.name }
            .filter { it.endsWith(MINOR_FILE_SUFFIX) }
            .map { it.removeSuffix(MINOR_FILE_SUFFIX) }
            .filter { it.matches(MINOR_REGEX) }
            .sortedWith(
                compareByDescending<String> { it.substringBefore('.').toIntOrNull() ?: 0 }
                    .thenByDescending { it.substringAfter('.').toIntOrNull() ?: 0 }
            )

    override suspend fun getChangelogMarkdown(
        minor: String,
        isActiveBranch: Boolean,
        language: String,
    ): String? {
        val lang = if (language in SUPPORTED_LANGUAGES) language else FALLBACK_LANGUAGE
        if (isActiveBranch) {
            return rootChangelog(lang) ?: rootChangelog(FALLBACK_LANGUAGE)
        }
        // Archived version: only ever the release-notes files (localized, then English). Never the
        // root changelog — that is the CURRENT active version and would be wrong for an old row.
        return archivedChangelog(lang, minor) ?: archivedChangelog(FALLBACK_LANGUAGE, minor)
    }

    private suspend fun archivedChangelog(lang: String, minor: String): String? =
        githubApi.getRawFile("release-notes/$lang/$minor.x.md")

    private suspend fun rootChangelog(lang: String): String? =
        githubApi.getRawFile("changelog_$lang.md")

    private companion object {
        val SUPPORTED_LANGUAGES = setOf("en", "ru")
        const val FALLBACK_LANGUAGE = "en"
        const val MINOR_FILE_SUFFIX = ".x.md"
        val MINOR_REGEX = Regex("""^\d+\.\d+$""")
    }
}
