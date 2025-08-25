package cash.p.terminal.wallet.managers

import kotlinx.coroutines.flow.StateFlow

interface UserManager {
    val currentUserLevelFlow: StateFlow<Int>

    fun getUserLevel(): Int
    fun setUserLevel(level: Int)
    fun allowAccountsForDuress(accountIds: List<String>)
    fun disallowAccountsForDuress()
}