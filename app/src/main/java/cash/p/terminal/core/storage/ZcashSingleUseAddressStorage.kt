package cash.p.terminal.core.storage

import cash.p.terminal.entities.ZcashSingleUseAddress
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class ZcashSingleUseAddressStorage(
    private val dao: ZcashSingleUseAddressDao,
    private val dispatcherProvider: DispatcherProvider
) {

    suspend fun saveAddress(address: ZcashSingleUseAddress) = withContext(dispatcherProvider.io) {
        dao.insert(address)
    }

    suspend fun getNextAddress(accountId: String): ZcashSingleUseAddress? =
        withContext(dispatcherProvider.io) {
            dao.getNextUnusedAddress(accountId)
        }

    suspend fun incrementUseCount(accountId: String, address: String) =
        withContext(dispatcherProvider.io) {
            dao.incrementUseCount(accountId, address)
        }

    suspend fun updateHadBalance(accountId: String, address: String, hadBalance: Boolean) =
        withContext(dispatcherProvider.io) {
            dao.updateHadBalance(accountId, address, hadBalance)
        }

    suspend fun getAddressesForBalanceCheck(accountId: String): List<ZcashSingleUseAddress> =
        withContext(dispatcherProvider.io) {
            dao.getAddressesWithoutBalance(accountId)
        }

    suspend fun deleteAccountAddresses(accountId: String) = withContext(dispatcherProvider.io) {
        dao.deleteByAccount(accountId)
    }
}
