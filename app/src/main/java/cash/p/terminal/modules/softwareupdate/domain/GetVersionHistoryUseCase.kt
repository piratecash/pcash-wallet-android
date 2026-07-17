package cash.p.terminal.modules.softwareupdate.domain

import cash.p.terminal.network.github.domain.entity.AppRelease
import cash.p.terminal.network.github.domain.repository.AppUpdateRepository
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

data class VersionHistory(
    val current: AppRelease?,
    val currentIsActiveBranch: Boolean,
    val oldMinors: List<String>,
)

class GetVersionHistoryUseCase(
    private val repository: AppUpdateRepository,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend operator fun invoke(): VersionHistory = withContext(dispatcherProvider.io) {
        val minors = repository.getVersionHistoryMinors()
        val current = try {
            repository.getLatestRelease()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val currentMinor = current?.minor
        VersionHistory(
            current = current,
            currentIsActiveBranch = current != null && currentMinor !in minors,
            oldMinors = minors.filter { it != currentMinor },
        )
    }
}
