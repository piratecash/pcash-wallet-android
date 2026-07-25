package cash.p.terminal.wallet.syncers

import android.util.Log
import cash.p.terminal.wallet.SyncInfo
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.models.BlockchainResponse
import cash.p.terminal.wallet.models.CoinResponse
import cash.p.terminal.wallet.models.TokenResponse
import cash.p.terminal.wallet.providers.HsProvider
import cash.p.terminal.wallet.storage.CoinStorage
import cash.p.terminal.wallet.storage.SyncerStateDao
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject

class CoinSyncer(
    private val hsProvider: HsProvider,
    private val storage: CoinStorage,
    private val syncerStateDao: SyncerStateDao,
    private val virtualCoinMapper: VirtualCoinMapper
) {
    private val keyCoinsLastSyncTimestamp = "coin-syncer-coins-last-sync-timestamp"
    private val keyBlockchainsLastSyncTimestamp = "coin-syncer-blockchains-last-sync-timestamp"
    private val keyTokensLastSyncTimestamp = "coin-syncer-tokens-last-sync-timestamp"
    private val keyCoinsCount = "coin-syncer-coins-count"
    private val keyBlockchainsCount = "coin-syncer-blockchains-count"
    private val keyTokensCount = "coin-syncer-tokens-count"
    private val keyLastRequestTimestamp = "coin-syncer-last-request-timestamp"
    private val keyServerAvailable = "coin-syncer-server-available"

    private var disposable: Disposable? = null

    val fullCoinsUpdatedObservable = PublishSubject.create<Unit>()

    fun sync(
        coinsTimestamp: Long,
        blockchainsTimestamp: Long,
        tokensTimestamp: Long,
        forceUpdate: Boolean
    ) {
        val lastCoinsSyncTimestamp = syncerStateDao.get(keyCoinsLastSyncTimestamp)?.toLong() ?: 0
        val coinsOutdated = lastCoinsSyncTimestamp != coinsTimestamp

        val lastBlockchainsSyncTimestamp =
            syncerStateDao.get(keyBlockchainsLastSyncTimestamp)?.toLong() ?: 0
        val blockchainsOutdated = lastBlockchainsSyncTimestamp != blockchainsTimestamp

        val lastTokensSyncTimestamp = syncerStateDao.get(keyTokensLastSyncTimestamp)?.toLong() ?: 0
        val tokensOutdated = lastTokensSyncTimestamp != tokensTimestamp

        if (!forceUpdate && !coinsOutdated && !blockchainsOutdated && !tokensOutdated) return

        syncerStateDao.save(keyLastRequestTimestamp, System.currentTimeMillis().toString())
        syncerStateDao.save(keyServerAvailable, "false")

        disposable = Single.zip(
            hsProvider.allCoinsSingle(),
            hsProvider.allBlockchainsSingle(),
            hsProvider.allTokensSingle()
        ) { r1, r2, r3 -> Triple(r1, r2, r3) }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe({ coinsData ->
                val (coinsResponse, blockchainsResponse, tokensResponse) = coinsData
                if (coinsResponse.isNotEmpty() && blockchainsResponse.isNotEmpty() && tokensResponse.isNotEmpty()) {
                    handleFetched(coinsResponse, blockchainsResponse, tokensResponse)
                    saveLastSyncTimestamps(coinsTimestamp, blockchainsTimestamp, tokensTimestamp)
                    syncerStateDao.save(keyServerAvailable, "true")
                }
            }, {
                Log.e("CoinSyncer", "sync() error", it)
                syncerStateDao.save(keyServerAvailable, "false")
            })
    }

    fun stop() {
        disposable?.dispose()
        disposable = null
    }

    private fun handleFetched(
        coinsResponse: List<CoinResponse>,
        blockchainsResponse: List<BlockchainResponse>,
        tokensResponse: List<TokenResponse>
    ) {
        val mapped = CoinResponseMapper.mapFetched(coinsResponse, blockchainsResponse, tokensResponse, virtualCoinMapper)

        storage.update(mapped.coins, mapped.blockchains, mapped.tokens)

        updateCounts()

        fullCoinsUpdatedObservable.onNext(Unit)
    }

    private fun updateCounts() {
        val coinsCount = storage.marketDatabase.coinDao().getCoinsCount()
        val blockchainsCount = storage.marketDatabase.coinDao().getBlockchainsCount()
        val tokensCount = storage.marketDatabase.coinDao().getTokensCount()

        syncerStateDao.save(keyCoinsCount, coinsCount.toString())
        syncerStateDao.save(keyBlockchainsCount, blockchainsCount.toString())
        syncerStateDao.save(keyTokensCount, tokensCount.toString())
    }

    private fun saveLastSyncTimestamps(coins: Long, blockchains: Long, tokens: Long) {
        syncerStateDao.save(keyCoinsLastSyncTimestamp, coins.toString())
        syncerStateDao.save(keyBlockchainsLastSyncTimestamp, blockchains.toString())
        syncerStateDao.save(keyTokensLastSyncTimestamp, tokens.toString())
    }

    fun syncInfo(): SyncInfo {
        if (syncerStateDao.get(keyCoinsCount) == null ||
            syncerStateDao.get(keyBlockchainsCount) == null ||
            syncerStateDao.get(keyTokensCount) == null
        ) {
            updateCounts()
        }

        val coinsCount = syncerStateDao.get(keyCoinsCount)?.toIntOrNull()
        val blockchainsCount = syncerStateDao.get(keyBlockchainsCount)?.toIntOrNull()
        val tokensCount = syncerStateDao.get(keyTokensCount)?.toIntOrNull()

        return SyncInfo(
            coinsTimestamp = syncerStateDao.get(keyCoinsLastSyncTimestamp),
            blockchainsTimestamp = syncerStateDao.get(keyBlockchainsLastSyncTimestamp),
            tokensTimestamp = syncerStateDao.get(keyTokensLastSyncTimestamp),
            coinsCount = coinsCount,
            blockchainsCount = blockchainsCount,
            tokensCount = tokensCount,
            serverAvailable = syncerStateDao.get(keyServerAvailable)?.toBooleanStrictOrNull()
        )
    }
}
