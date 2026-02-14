package cash.p.terminal.modules.balance

import cash.p.terminal.core.storage.EnabledWalletsCacheDao
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.EnabledWalletCache
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.getUniqueKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BalanceCache(private val dao: EnabledWalletsCacheDao) {
    private val mutex = Mutex()
    private var cacheMap: Map<String, BalanceData>

    init {
        cacheMap = convertToCacheMap(dao.getAll())
    }

    private fun convertToCacheMap(list: List<EnabledWalletCache>): Map<String, BalanceData> {
        return list.map {
            val key = listOf(it.tokenQueryId, it.accountId).joinToString()
            key to BalanceData(
                available = it.balance,
                timeLocked = it.balanceLocked,
                stackingUnpaid = it.stackingUnpaid
            )
        }.toMap()
    }

    suspend fun setCache(wallet: Wallet, balanceData: BalanceData) {
        setCache(mapOf(wallet to balanceData))
    }

    fun getCache(wallet: Wallet): BalanceData? {
        val key = wallet.getUniqueKey()
        return cacheMap[key]
    }

    suspend fun setCache(balancesData: Map<Wallet, BalanceData>) {
        val list = balancesData.map { (wallet, balanceData) ->
            EnabledWalletCache(
                tokenQueryId = wallet.token.tokenQuery.id,
                accountId = wallet.account.id,
                balance = balanceData.available,
                balanceLocked = balanceData.timeLocked,
                stackingUnpaid = balanceData.stackingUnpaid
            )
        }

        if (list.isEmpty()) return

        val successfulInserts = list.mapNotNull { entry ->
            tryOrNull { dao.insert(entry) }?.let { entry }
        }

        if (successfulInserts.isNotEmpty()) {
            mutex.withLock {
                cacheMap = cacheMap + convertToCacheMap(successfulInserts)
            }
        }
    }

}
