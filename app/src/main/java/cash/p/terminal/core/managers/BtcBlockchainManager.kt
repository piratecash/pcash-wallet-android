package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.entities.TransactionDataSortMode
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.bitcoincore.BitcoinCore.SyncMode
import io.horizontalsystems.core.entities.BlockchainType
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class BtcBlockchainManager(
    private val storage: BlockchainSettingsStorage,
    marketKit: MarketKitWrapper,
) {

    private val restoreModeUpdatedSubject = PublishSubject.create<BlockchainType>()
    val restoreModeUpdatedObservable: Observable<BlockchainType> = restoreModeUpdatedSubject

    private val transactionSortModeUpdatedSubject = PublishSubject.create<BlockchainType>()
    val transactionSortModeUpdatedObservable: Observable<BlockchainType> =
        transactionSortModeUpdatedSubject

    private val blockchairSyncEnabledBlockchains =
        listOf(
            BlockchainType.Bitcoin,
            BlockchainType.BitcoinCash,
            BlockchainType.Dogecoin,
            BlockchainType.PirateCash,
            BlockchainType.Cosanta,
            BlockchainType.Litecoin
        )

    val blockchainTypes by lazy {
        listOf(
            BlockchainType.Bitcoin,
            BlockchainType.BitcoinCash,
            BlockchainType.Litecoin,
            BlockchainType.Dash,
            BlockchainType.Dogecoin,
            BlockchainType.PirateCash,
            BlockchainType.Cosanta,
            BlockchainType.ECash,
        )
    }

    val allBlockchains by lazy { marketKit.blockchains(blockchainTypes.map { it.uid }) }

    fun blockchain(blockchainType: BlockchainType) =
        allBlockchains.firstOrNull { blockchainType == it.type }

    private fun defaultRestoreMode(blockchainType: BlockchainType) =
        if (blockchainType in blockchairSyncEnabledBlockchains) BtcRestoreMode.Blockchair else BtcRestoreMode.Hybrid

    fun restoreMode(blockchainType: BlockchainType): BtcRestoreMode {
        return storage.btcRestoreMode(blockchainType) ?: defaultRestoreMode(blockchainType)
    }

    fun availableRestoreModes(blockchainType: BlockchainType) =
        BtcRestoreMode.values().let {
            val values = it.toList()
            if (blockchainType !in blockchairSyncEnabledBlockchains) {
                values - BtcRestoreMode.Blockchair
            } else {
                values
            }
        }

    fun syncMode(blockchainType: BlockchainType, accountOrigin: AccountOrigin): SyncMode {
        if (accountOrigin == AccountOrigin.Created && blockchainType in blockchairSyncEnabledBlockchains) {
            return SyncMode.Blockchair()
        }

        return when (restoreMode(blockchainType)) {
            BtcRestoreMode.Blockchair -> SyncMode.Blockchair()
            BtcRestoreMode.Hybrid -> SyncMode.Api()
            BtcRestoreMode.Blockchain -> SyncMode.Full()
        }
    }

    fun save(restoreMode: BtcRestoreMode, blockchainType: BlockchainType) {
        storage.save(restoreMode, blockchainType)
        restoreModeUpdatedSubject.onNext(blockchainType)
    }

    fun transactionSortMode(blockchainType: BlockchainType): TransactionDataSortMode {
        return storage.btcTransactionSortMode(blockchainType) ?: TransactionDataSortMode.Shuffle
    }

    fun save(transactionSortMode: TransactionDataSortMode, blockchainType: BlockchainType) {
        storage.save(transactionSortMode, blockchainType)
        transactionSortModeUpdatedSubject.onNext(blockchainType)
    }
}
