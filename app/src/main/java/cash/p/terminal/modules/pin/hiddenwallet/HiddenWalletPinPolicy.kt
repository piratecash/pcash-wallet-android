package cash.p.terminal.modules.pin.hiddenwallet

import cash.p.terminal.wallet.IAccountsStorage
import io.horizontalsystems.core.IPinComponent

class HiddenWalletPinPolicy(
    private val pinComponent: IPinComponent,
    private val accountsStorage: IAccountsStorage,
) {

    fun canUse(pin: String): Boolean {
        val existingLevel = pinComponent.getPinLevel(pin) ?: return true
        if (existingLevel >= 0) return false

        return accountsStorage.getWalletsCountByLevel(existingLevel) == 0
    }
}
