package cash.p.terminal.modules.softwareupdate.domain

import cash.p.terminal.network.github.domain.entity.AppRelease

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

enum class UpdateCheckInterval(val millis: Long) {
    DAY(DAY_MILLIS),
    WEEK(7 * DAY_MILLIS),
    MONTH(30 * DAY_MILLIS);
}

enum class InstallSource { GOOGLE_PLAY, FDROID, OTHER }

data class ChangelogSnippet(val improvements: Int, val fixes: Int)

sealed interface UpdateStatus {
    data object Unknown : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(
        val release: AppRelease,
        val changelogSnippet: ChangelogSnippet?,
    ) : UpdateStatus

    data object Error : UpdateStatus
}
