package cash.p.terminal.wallet.managers

import kotlinx.coroutines.flow.StateFlow

interface UserManager {
    companion object {
        const val DEFAULT_USER_LEVEL = Int.MAX_VALUE
    }
    val currentUserLevelFlow: StateFlow<Int>

    fun getUserLevel(): Int
    fun setUserLevel(level: Int)
    fun allowAccountsForDuress(accountIds: List<String>)
    fun disallowAccountsForDuress()
}