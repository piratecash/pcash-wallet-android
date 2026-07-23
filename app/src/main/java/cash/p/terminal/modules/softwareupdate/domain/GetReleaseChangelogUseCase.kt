package cash.p.terminal.modules.softwareupdate.domain

import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.network.github.domain.repository.AppUpdateRepository
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class GetReleaseChangelogUseCase(
    private val repository: AppUpdateRepository,
    private val languageManager: LanguageManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend operator fun invoke(minor: String, isActiveBranch: Boolean): String? =
        withContext(dispatcherProvider.io) {
            repository.getChangelogMarkdown(minor, isActiveBranch, languageManager.currentLanguage)
        }
}
