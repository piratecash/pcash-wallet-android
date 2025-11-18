package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.entities.ZcashSingleUseAddress

class ZcashSingleUseAddressManager(
    private val storage: ZcashSingleUseAddressStorage,
    private val accountId: String
) {

    suspend fun saveNewAddress(address: String) {
        storage.saveAddress(
            ZcashSingleUseAddress(
                accountId = accountId,
                address = address,
                useCount = 1,
                hadBalance = false
            )
        )
    }

    suspend fun getNextAddress(): String? {
        return storage.getNextAddress(accountId)?.address?.also {
            markAddressAsShown(it)
        }
    }

    suspend fun markAddressAsShown(address: String) {
        storage.incrementUseCount(accountId, address)
    }

    suspend fun updateAddressBalance(address: String, hasBalance: Boolean) {
        if (hasBalance) {
            storage.updateHadBalance(accountId, address, true)
        }
    }

    suspend fun getAddressesForBalanceCheck(): List<String> {
        return storage.getAddressesForBalanceCheck(accountId).map { it.address }
    }
}
