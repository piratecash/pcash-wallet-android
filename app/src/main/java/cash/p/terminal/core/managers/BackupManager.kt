package cash.p.terminal.core.managers

import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.core.IBackupManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BackupManager(private val accountManager: IAccountManager) : IBackupManager {

    override val allBackedUp: Boolean
        get() = accountManager.accounts.all { !it.supportsBackup || it.isBackedUp }

    override val allBackedUpFlow: Flow<Boolean>
        get() = accountManager.accountsFlow.map { accounts ->
            accounts.all { it.isBackedUp }
        }
}
