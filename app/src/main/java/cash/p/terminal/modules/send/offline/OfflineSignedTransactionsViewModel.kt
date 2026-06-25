package cash.p.terminal.modules.send.offline

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.OfflineTransactionStatusAdapter
import cash.p.terminal.core.canonicalTransactionHash
import cash.p.terminal.core.defaultTokenQuery
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.nativeTokenQueries
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.entities.OfflineSignedTransactionStatus
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionsRateRepository
import cash.p.terminal.modules.transactions.currencyValue
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.meta
import cash.p.terminal.wallet.title
import cash.p.terminal.wallet.tokenQueryId
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.math.BigDecimal

class OfflineSignedTransactionsViewModel(
    private val repository: OfflineSignedTransactionRepository,
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager,
    private val transactionAdapterManager: TransactionAdapterManager,
    private val marketKit: MarketKitWrapper,
    private val rateRepository: TransactionsRateRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModelUiState<OfflineSignedTransactionsUiState>() {

    private var items: List<OfflineSignedTransactionViewItem> = emptyList()
    private var statusReconciliationJob: Job? = null

    override fun createState() = OfflineSignedTransactionsUiState(items = items)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val itemsFlow = accountManager.activeAccountStateFlow
        .map { (it as? ActiveAccountState.ActiveAccount)?.account }
        .distinctUntilChanged()
        .flatMapLatest { account ->
            if (account == null) {
                flowOf(OfflineSignedTransactionsInput(account = null, entities = emptyList()))
            } else {
                repository.observe(account.id)
                    .map { entities ->
                        OfflineSignedTransactionsInput(account = account, entities = entities)
                    }
            }
        }
        .combine(walletManager.activeWalletsFlow) { input, wallets ->
            OfflineSignedTransactionsSnapshot(
                account = input.account,
                entities = input.entities,
                wallets = wallets,
            )
        }

    init {
        viewModelScope.launch(dispatcherProvider.default) {
            itemsFlow.collect { snapshot ->
                items = snapshot.entities.mapNotNull { it.toViewItem(snapshot.account, snapshot.wallets) }
                reconcileBroadcastedStatuses(snapshot)
                emitState()
            }
        }
    }

    private fun reconcileBroadcastedStatuses(snapshot: OfflineSignedTransactionsSnapshot) {
        statusReconciliationJob?.cancel()
        val accountId = snapshot.account?.id ?: return
        val pendingEntities = snapshot.pendingEntities()
        if (pendingEntities.isEmpty()) return
        val pendingBySource = snapshot.pendingEntitiesBySource(pendingEntities)

        statusReconciliationJob = viewModelScope.launch(dispatcherProvider.io) {
            repository.markBroadcastedRawDuplicates(accountId)
            if (pendingBySource.isEmpty()) return@launch

            transactionAdapterManager.adaptersReadyFlow.collectLatest { adapters ->
                val recordFlows = pendingBySource.recordFlows(adapters)
                if (recordFlows.isEmpty()) return@collectLatest

                recordFlows.merge().collect { adapterRecords ->
                    markBroadcastedRecords(adapterRecords)
                }
            }
        }
    }

    private fun OfflineSignedTransactionsSnapshot.pendingEntities(): List<OfflineSignedTransactionEntity> =
        entities.filter { OfflineSignedTransactionStatus.from(it.status) == OfflineSignedTransactionStatus.Pending }

    private fun OfflineSignedTransactionsSnapshot.pendingEntitiesBySource(
        pendingEntities: List<OfflineSignedTransactionEntity>,
    ): Map<TransactionSource, List<PendingOfflineEntity>> =
        pendingEntities
            .mapNotNull { entity ->
                val wallet = entity.sourceWallet(wallets)
                if (wallet == null) return@mapNotNull null
                wallet.transactionSource to PendingOfflineEntity(entity, wallet)
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )

    private fun Map<TransactionSource, List<PendingOfflineEntity>>.recordFlows(
        adapters: Map<TransactionSource, ITransactionsAdapter>,
    ): List<Flow<OfflineSignedAdapterRecords>> =
        mapNotNull { (source, pendingEntities) ->
            val adapter = adapters[source] ?: return@mapNotNull null
            adapter.getTransactionRecordsFlow(
                token = null,
                transactionType = FilterTransactionType.All,
                address = null,
            ).map { records ->
                OfflineSignedAdapterRecords(
                    adapter = adapter,
                    pendingEntities = pendingEntities,
                    records = records,
                )
            }
        }

    private suspend fun markBroadcastedRecords(adapterRecords: OfflineSignedAdapterRecords) {
        adapterRecords.pendingEntities.forEach { pending ->
            val entity = pending.entity
            val confirmedTxHash = adapterRecords.findConfirmedTxHash(entity)
                ?: pending.confirmedByStatusAdapter()
                ?: return@forEach
            repository.markBroadcasted(
                accountId = entity.accountId,
                txHash = entity.txHash,
                confirmedTxHash = confirmedTxHash,
            )
        }
    }

    private suspend fun PendingOfflineEntity.confirmedByStatusAdapter(): String? {
        if (entity.blockchainTypeUid !in statusAdapterBlockchainUids) return null
        val adapter = adapterManager.getAdapterForWalletOld(wallet) as? OfflineTransactionStatusAdapter
            ?: return null
        val exists = try {
            adapter.transactionExists(entity.txHash)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            false
        }
        return if (exists) entity.txHash.offlineComparableHash(entity.blockchainTypeUid) else null
    }

    private fun OfflineSignedAdapterRecords.findConfirmedTxHash(
        entity: OfflineSignedTransactionEntity,
    ): String? =
        records.firstOrNull {
            it.transactionHash.offlineComparableHash(entity.blockchainTypeUid) ==
                entity.txHash.offlineComparableHash(entity.blockchainTypeUid)
        }
            ?.transactionHash
            ?.offlineComparableHash(entity.blockchainTypeUid)
            ?: records.firstOrNull { record ->
                adapter.rawTransactionMatches(record.transactionHash, entity.rawHex)
            }?.transactionHash?.offlineComparableHash(entity.blockchainTypeUid)

    private fun ITransactionsAdapter.rawTransactionMatches(
        transactionHash: String,
        rawHex: String,
    ): Boolean =
        tryOrNull { getRawTransaction(transactionHash)?.normalizedHex() } == rawHex.normalizedHex()

    private fun String.normalizedHex(): String = trim().lowercase()

    private fun String.offlineComparableHash(blockchainTypeUid: String): String =
        if (blockchainTypeUid == BlockchainType.Solana.uid) {
            trim()
        } else {
            canonicalTransactionHash()
        }

    private fun OfflineSignedTransactionEntity.toViewItem(
        account: Account?,
        wallets: List<Wallet>,
    ): OfflineSignedTransactionViewItem? {
        val metadataUnknown = hasUnknownRawMetadata
        val amountValue = displayAmount(metadataUnknown)
        val status = OfflineSignedTransactionStatus.from(status)
        val sourceWallet = sourceWallet(wallets)
        val token = resolveDisplayToken(wallets)
        if (token == null) return null
        val source = sourceWallet?.transactionSource
            ?: account?.let { TransactionSource(token.blockchain, it, token.type.meta) }
        if (source == null) return null
        val walletUid = sourceWallet?.tokenQueryId ?: token.tokenQuery.id
        val transactionItem = transactionItem(token, source, walletUid, amountValue, metadataUnknown, status)
        return OfflineSignedTransactionViewItem(
            uid = OFFLINE_SIGNED_UID_PREFIX + txHash,
            txHash = txHash,
            transactionItem = transactionItem,
            statusValue = status.toColoredValue(),
            metadataUnknown = metadataUnknown,
        )
    }

    private fun OfflineSignedTransactionEntity.sourceWallet(wallets: List<Wallet>): Wallet? {
        if (sourceTokenQueryId.isNotBlank()) return wallets.walletForTokenQueryId(sourceTokenQueryId)

        return wallets.walletFor(
            blockchainType = BlockchainType.fromUid(blockchainTypeUid),
            coinCode = coinCode,
            tokenDecimals = tokenDecimals,
        )
    }

    private fun OfflineSignedTransactionEntity.resolveDisplayToken(wallets: List<Wallet>): Token? {
        if (tokenQueryId.isNotBlank()) {
            val query = TokenQuery.fromId(tokenQueryId) ?: return cachedToken(null)
            return wallets.walletForTokenQueryId(query.id)?.token
                ?: marketKit.token(query)
                ?: cachedToken(query)
        }
        return resolveLegacyToken()
    }

    private fun List<Wallet>.walletFor(
        blockchainType: BlockchainType,
        coinCode: String,
        tokenDecimals: Int,
    ): Wallet? =
        firstOrNull { wallet ->
            wallet.token.blockchainType == blockchainType &&
                wallet.token.coin.code == coinCode &&
                wallet.token.decimals == tokenDecimals
        }

    private fun List<Wallet>.walletForTokenQueryId(tokenQueryId: String): Wallet? {
        if (tokenQueryId.isBlank()) return null
        return firstOrNull { it.tokenQueryId == tokenQueryId }
    }

    private fun OfflineSignedTransactionEntity.resolveLegacyToken(): Token? {
        val blockchainType = BlockchainType.fromUid(blockchainTypeUid)
        val tokenQueries = (listOf(blockchainType.defaultTokenQuery) + blockchainType.nativeTokenQueries)
            .distinct()
        return marketKit.tokens(tokenQueries)
            .firstOrNull { token ->
                token.blockchainType == blockchainType &&
                    token.coin.code == coinCode &&
                    token.decimals == tokenDecimals
            }
    }

    private fun OfflineSignedTransactionEntity.cachedToken(query: TokenQuery?): Token? {
        if (coinCode.isBlank()) return null
        val blockchainType = query?.blockchainType ?: BlockchainType.fromUid(blockchainTypeUid)
        val tokenType = query?.tokenType ?: blockchainType.defaultTokenQuery.tokenType
        return Token(
            coin = Coin(
                uid = coinUid ?: coinCode,
                name = coinName ?: coinCode,
                code = coinCode,
            ),
            blockchain = Blockchain(
                type = blockchainType,
                name = blockchainType.title,
                eip3091url = null,
            ),
            type = tokenType,
            decimals = tokenDecimals,
        )
    }

    private fun OfflineSignedTransactionEntity.displayAmount(metadataUnknown: Boolean): BigDecimal {
        if (metadataUnknown) return BigDecimal.ZERO

        val amountValue = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        if (BlockchainType.fromUid(blockchainTypeUid) != BlockchainType.Zcash) return amountValue
        if (feeTokenQueryId != tokenQueryId) return amountValue

        val feeValue = feeAtomic
            ?.toBigDecimalOrNull()
            ?.movePointLeft(tokenDecimals)
            ?: return amountValue

        return amountValue + feeValue
    }

    private fun OfflineSignedTransactionEntity.transactionItem(
        token: Token,
        source: TransactionSource,
        walletUid: String,
        amount: BigDecimal,
        metadataUnknown: Boolean,
        status: OfflineSignedTransactionStatus,
    ): TransactionItem {
        val record = PendingTransactionRecord(
            uid = OFFLINE_SIGNED_UID_PREFIX + txHash,
            transactionHash = txHash,
            timestamp = createdAt / MILLISECONDS_IN_SECOND,
            source = source,
            token = token,
            amount = amount,
            toAddress = toAddress,
            fromAddress = "",
            expiresAt = Long.MAX_VALUE,
            memo = null,
        )
        return TransactionItem(
            record = record,
            currencyValue = if (metadataUnknown) null else record.currencyValue(rateRepository),
            lastBlockInfo = null,
            nftMetadata = emptyMap(),
            walletUid = walletUid,
            offlineStatus = status.toInfoColoredValue(),
        )
    }

    private val OfflineSignedTransactionEntity.hasUnknownRawMetadata: Boolean
        get() = pcashPayload.isBlank() && toAddress.isBlank()

    private fun OfflineSignedTransactionStatus.toColoredValue(): ColoredValue =
        when (this) {
            OfflineSignedTransactionStatus.Pending -> ColoredValue(
                Translator.getString(R.string.offline_signed_status_pending),
                ColorName.Jacob,
            )

            OfflineSignedTransactionStatus.Broadcasted -> ColoredValue(
                Translator.getString(R.string.offline_signed_status_broadcasted),
                ColorName.Remus,
            )
        }

    private fun OfflineSignedTransactionStatus.toInfoColoredValue(): ColoredValue =
        when (this) {
            OfflineSignedTransactionStatus.Pending -> ColoredValue(
                Translator.getString(R.string.offline_signed_status_pending),
                ColorName.Jacob,
            )

            OfflineSignedTransactionStatus.Broadcasted -> ColoredValue(
                Translator.getString(R.string.TransactionInfo_Sent),
                ColorName.Remus,
            )
        }

    override fun onCleared() {
        statusReconciliationJob?.cancel()
        rateRepository.clear()
    }
}

private data class OfflineSignedTransactionsInput(
    val account: Account?,
    val entities: List<OfflineSignedTransactionEntity>,
)

private data class OfflineSignedTransactionsSnapshot(
    val account: Account?,
    val entities: List<OfflineSignedTransactionEntity>,
    val wallets: List<Wallet>,
)

private data class PendingOfflineEntity(
    val entity: OfflineSignedTransactionEntity,
    val wallet: Wallet,
)

private data class OfflineSignedAdapterRecords(
    val adapter: ITransactionsAdapter,
    val pendingEntities: List<PendingOfflineEntity>,
    val records: List<TransactionRecord>,
)

data class OfflineSignedTransactionViewItem(
    val uid: String,
    val txHash: String,
    val transactionItem: TransactionItem,
    val statusValue: ColoredValue,
    val metadataUnknown: Boolean,
)

data class OfflineSignedTransactionsUiState(
    val items: List<OfflineSignedTransactionViewItem>,
)

private const val OFFLINE_SIGNED_UID_PREFIX = "offline-signed:"
private const val MILLISECONDS_IN_SECOND = 1000L

private val statusAdapterBlockchainUids = setOf(
    BlockchainType.Ton.uid,
    BlockchainType.Stellar.uid,
)
