package cash.p.terminal.core.factories

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.DefaultUserManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import java.util.UUID

class AccountFactory(
    private val accountManager: IAccountManager,
    private val userManager: DefaultUserManager
) : IAccountFactory {

    override fun account(
        name: String,
        type: AccountType,
        origin: AccountOrigin,
        backedUp: Boolean,
        fileBackedUp: Boolean
    ): Account {
        val id = UUID.randomUUID().toString()

        return Account(
            id = id,
            name = name,
            type = type,
            origin = origin,
            level = userManager.getUserLevel(),
            isBackedUp = backedUp,
            isFileBackedUp = fileBackedUp
        )
    }

    override fun watchAccount(name: String, type: AccountType): Account {
        val id = UUID.randomUUID().toString()
        return Account(
            id = id,
            name = name,
            type = type,
            origin = AccountOrigin.Restored,
            level = userManager.getUserLevel(),
            isBackedUp = true
        )
    }

    override fun getNextWatchAccountName(): String {
        val watchAccountsCount = accountManager.accounts.count { it.isWatchAccount }
        return getUniqueName("Watch Wallet ${watchAccountsCount + 1}")
    }

    override fun getNextAccountName(): String {
        val nonWatchAccountsCount =
            accountManager.accounts.count { !it.isWatchAccount && it.type !is AccountType.HardwareCard }
        return getUniqueName("Wallet ${nonWatchAccountsCount + 1}")
    }

    override fun getNextHardwareAccountName(): String {
        val hardWalletAccountsCount =
            accountManager.accounts.count { it.type is AccountType.HardwareCard }
        return getUniqueName("Hardware Wallet ${hardWalletAccountsCount + 1}")
    }

    override fun getUniqueName(name: String, additionalExistingNames: Set<String>): String {
        val existingNames = buildSet {
            accountManager.accounts.mapTo(this) { it.name }
            addAll(additionalExistingNames)
        }
        if (name !in existingNames) return name
        var counter = 1
        while ("$name ($counter)" in existingNames) counter++
        return "$name ($counter)"
    }
}
