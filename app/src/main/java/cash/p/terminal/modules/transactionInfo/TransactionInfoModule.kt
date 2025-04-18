package cash.p.terminal.modules.transactionInfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.getKoinInstance
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.nft.NftAssetBriefMetadata
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.transactions.NftMetadataService
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionStatus
import io.horizontalsystems.core.entities.BlockchainType

object TransactionInfoModule {

    class Factory(private val transactionItem: TransactionItem) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val transactionSource = transactionItem.record.source
            val adapter: ITransactionsAdapter = App.transactionAdapterManager.getAdapter(transactionSource)!!
            val service = TransactionInfoService(
                transactionRecord = transactionItem.record,
                changeNowTransactionId = transactionItem.changeNowTransactionId,
                adapter = adapter,
                marketKit = App.marketKit,
                currencyManager = App.currencyManager,
                nftMetadataService = NftMetadataService(App.nftMetadataManager),
                balanceHidden = App.balanceHiddenManager.balanceHidden,
                transactionStatusUrl = transactionItem.transactionStatusUrl,
                updateChangeNowStatusesUseCase = getKoinInstance()
            )
            val factory = TransactionInfoViewItemFactory(
                transactionSource.blockchain.type.resendable,
                transactionSource.blockchain.type
            )

            return TransactionInfoViewModel(service, factory, App.contactsRepository) as T
        }

    }

    data class ExplorerData(val title: String, val url: String?)
}

sealed class TransactionStatusViewItem(val name: Int) {
    object Pending : TransactionStatusViewItem(R.string.Transactions_Pending)

    //progress in 0.0 .. 1.0
    class Processing(val progress: Float) : TransactionStatusViewItem(R.string.Transactions_Processing)
    object Completed : TransactionStatusViewItem(R.string.Transactions_Completed)
    object Failed : TransactionStatusViewItem(R.string.Transactions_Failed)
}

data class TransactionInfoItem(
    val record: TransactionRecord,
    val externalStatus: TransactionStatus?,
    val lastBlockInfo: LastBlockInfo?,
    val explorerData: TransactionInfoModule.ExplorerData,
    val rates: Map<String, CurrencyValue>,
    val nftMetadata: Map<NftUid, NftAssetBriefMetadata>,
    val hideAmount: Boolean,
    val transactionStatusUrl: Pair<String, String>? = null
)

val BlockchainType.resendable: Boolean
    get() =
        when (this) {
            BlockchainType.Optimism, BlockchainType.Base, BlockchainType.ZkSync, BlockchainType.ArbitrumOne -> false
            else -> true
        }
