package cash.p.terminal.core.adapters

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.managers.TronKitWrapper
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.ethereumkit.core.hexStringToByteArrayOrNull
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.hexStringToByteArray
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.TransactionTag
import io.horizontalsystems.tronkit.network.Network
import io.reactivex.Flowable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx2.asFlowable

class TronTransactionsAdapter(
    val tronKitWrapper: TronKitWrapper,
    private val transactionConverter: TronTransactionConverter
) : ITransactionsAdapter {

    private val tronKit = tronKitWrapper.tronKit

    override val explorerTitle: String
        get() = "Tronscan"

    override fun getTransactionUrl(transactionHash: String): String = when (tronKit.network) {
        Network.Mainnet -> "https://tronscan.io/#/transaction/$transactionHash"
        Network.ShastaTestnet -> "https://shasta.tronscan.org/#/transaction/$transactionHash"
        Network.NileTestnet -> "https://nile.tronscan.org/#/transaction/$transactionHash"
    }

    override val lastBlockInfo: LastBlockInfo
        get() = tronKit.lastBlockHeight.toInt().let { LastBlockInfo(it) }

    override val lastBlockUpdatedFlowable: Flowable<Unit>
        get() = tronKit.lastBlockHeightFlow.asFlowable().map { }

    override val transactionsState: AdapterState
        get() = convertToAdapterState(tronKit.syncState)

    override val transactionsStateUpdatedFlowable: Flowable<Unit>
        get() = tronKit.syncStateFlow.asFlowable().map {}

    override suspend fun getTransactions(
        from: TransactionRecord?,
        token: Token?,
        limit: Int,
        transactionType: FilterTransactionType,
        address: String?,
    ): List<TransactionRecord> = tronKit.getFullTransactionsBefore(
        getFilters(token, transactionType, address),
        from?.transactionHash?.hexStringToByteArray(),
        limit
    ).map {
        transactionConverter.transactionRecord(it)
    }

    override suspend fun getTransactionsAfter(fromTransactionId: String?): List<TransactionRecord> {
        return tronKit.getFullTransactionsAfter(
            listOf(),
            fromTransactionId?.hexStringToByteArrayOrNull()
        )
            .map {
                transactionConverter.transactionRecord(it)
            }
    }

    override fun getTransactionRecordsFlow(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?,
    ): Flow<List<TransactionRecord>> {
        return tronKit.getFullTransactionsFlow(getFilters(token, transactionType, address))
            .map { transactions ->
                transactions.map { transactionConverter.transactionRecord(it) }
            }
    }

    private fun convertToAdapterState(syncState: TronKit.SyncState): AdapterState =
        when (syncState) {
            is TronKit.SyncState.Synced -> AdapterState.Synced
            is TronKit.SyncState.NotSynced -> AdapterState.NotSynced(syncState.error)
            is TronKit.SyncState.Syncing -> AdapterState.Syncing()
        }

    private fun coinTags(token: Token) = when (val type = token.type) {
        TokenType.Native -> listOf(TransactionTag.TRX_COIN)
        is TokenType.Eip20 -> listOf(type.address)
        is TokenType.Trc10 -> listOf(
            directionTag(token, incoming = true),
            directionTag(token, incoming = false),
        )

        else -> listOf("")
    }

    private fun directionTag(token: Token, incoming: Boolean) =
        when (val type = token.type) {
            TokenType.Native ->
                if (incoming) TransactionTag.TRX_COIN_INCOMING else TransactionTag.TRX_COIN_OUTGOING

            is TokenType.Eip20 ->
                if (incoming) {
                    TransactionTag.trc20Incoming(type.address)
                } else {
                    TransactionTag.trc20Outgoing(type.address)
                }

            is TokenType.Trc10 ->
                if (incoming) {
                    TransactionTag.trc10Incoming(type.assetId)
                } else {
                    TransactionTag.trc10Outgoing(type.assetId)
                }

            else -> ""
        }

    private fun getFilters(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?,
    ) = buildList {
        token?.let {
            add(coinTags(it))
        }

        val filterType = when (transactionType) {
            FilterTransactionType.All -> null
            FilterTransactionType.Incoming -> when {
                token != null -> directionTag(token, incoming = true)
                else -> TransactionTag.INCOMING
            }

            FilterTransactionType.Outgoing -> when {
                token != null -> directionTag(token, incoming = false)
                else -> TransactionTag.OUTGOING
            }

            FilterTransactionType.Swap -> TransactionTag.SWAP
            FilterTransactionType.Approve -> TransactionTag.TRC20_APPROVE
        }

        filterType?.let {
            add(listOf(it))
        }

        val addressHex = address?.let { Address.fromBase58(it).hex }?.lowercase()
        if (!addressHex.isNullOrBlank()) {
            add(listOf("from_$addressHex", "to_$addressHex"))
        }
    }
}
