package cash.p.terminal.modules.softwareupdate.domain

import cash.p.terminal.core.ILocalStorage

class ShouldAutoCheckUseCase(
    private val localStorage: ILocalStorage,
    private val timeProvider: TimeProvider,
) {
    operator fun invoke(): Boolean =
        timeProvider.now() - localStorage.lastUpdateCheckTimestamp >= localStorage.updateCheckInterval.millis
}
