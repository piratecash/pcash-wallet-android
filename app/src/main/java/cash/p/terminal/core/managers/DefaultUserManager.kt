package cash.p.terminal.core.managers

import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DefaultUserManager(
    private val accountManager: IAccountManager
): UserManager {
    private val logger: AppLogger = AppLogger("DefaultUserManager")

    private var currentUserLevel = Int.MAX_VALUE

    private val _currentUserLevelFlow = MutableStateFlow(currentUserLevel)
    override val currentUserLevelFlow: StateFlow<Int> = _currentUserLevelFlow.asStateFlow()

    override fun getUserLevel() = currentUserLevel

    override fun setUserLevel(level: Int) {
        if (level == currentUserLevel) {
            logger.info("User level is already set to $level")
            return
        }

        currentUserLevel = level
        _currentUserLevelFlow.update { level }
        accountManager.setLevel(level)
    }

    override fun allowAccountsForDuress(accountIds: List<String>) {
        accountManager.updateAccountLevels(accountIds, currentUserLevel + 1)
    }

    override fun disallowAccountsForDuress() {
        accountManager.updateMaxLevel(currentUserLevel)
    }
}
