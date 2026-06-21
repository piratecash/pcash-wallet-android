package cash.p.terminal.modules.send.offline

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.EvmError
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineBroadcastAdapter
import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.core.convertedError
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.nativeTokenQueries
import cash.p.terminal.core.order
import cash.p.terminal.core.supported
import cash.p.terminal.core.supports
import cash.p.terminal.core.toResString
import cash.p.terminal.entities.DecodedOfflineTransaction
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

class OfflineBroadcastViewModel(
    private val payloadEncoder: OfflineTransactionPayloadEncoder,
    private val offlineSignedTransactionRepository: OfflineSignedTransactionRepository,
    private val walletManager: IWalletManager,
    private val accountManager: IAccountManager,
    private val adapterManager: IAdapterManager,
    private val walletUseCase: WalletUseCase,
    private val marketKit: MarketKitWrapper,
    private val offlineBroadcastTokenResolver: OfflineBroadcastTokenResolver,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModelUiState<OfflineBroadcastUiState>() {

    private var step = OfflineBroadcastStep.Loading
    private var rawHex = ""
    private var networkSelectable = false
    private var selectableBlockchains: List<Blockchain> = emptyList()
    private var selectedBlockchain: Blockchain? = null
    private var targetWallet: Wallet? = null
    private var confirmAction: OfflineBroadcastConfirmAction = OfflineBroadcastConfirmAction.Send
    private var tokenToEnable: Token? = null
    private var pendingImport: PendingImport? = null
    private var importJob: Job? = null
    private var broadcasting = false
    private var result: OfflineBroadcastResult? = null
    private var dismissError: String? = null
    private var errorMessage: String? = null
    private var prefilled = false
    private var offlineRecordKey: OfflineRecordKey? = null

    override fun createState() = OfflineBroadcastUiState(
        step = step,
        confirm = confirmState(),
        selectableBlockchains = selectableBlockchains,
        selectedBlockchain = selectedBlockchain,
        broadcasting = broadcasting,
        result = result,
        dismissError = dismissError,
        errorMessage = errorMessage,
    )

    private fun confirmState(): OfflineBroadcastConfirm? =
        rawHex.takeIf { it.isNotEmpty() }?.let {
            OfflineBroadcastConfirm(
                rawHex = it,
                selectable = networkSelectable,
                blockchainName = selectedBlockchain?.name,
                action = confirmAction,
            )
        }

    // The scanner is the only input source. Decode the scanned payload and open the confirmation
    // screen with the network fixed (pcash payload) or selectable (plain RAW HEX).
    fun prefillAndAdvance(value: String) {
        if (prefilled) return
        prefilled = true

        val text = value.trim()
        if (text.isEmpty()) {
            dismissWithError(R.string.offline_broadcast_invalid_input)
            return
        }
        val decoded = payloadEncoder.decode(text)
        if (decoded != null) prepareFromDecoded(decoded, text) else prepareFromRawHex(text)
    }

    fun onPickNetwork() {
        if (!networkSelectable) return
        step = OfflineBroadcastStep.SelectBlockchain
        emitState()
    }

    fun onSelectBlockchain(blockchain: Blockchain) {
        val wallet = walletFor(blockchain.type)
        selectedBlockchain = blockchain
        targetWallet = wallet
        tokenToEnable = null
        confirmAction = selectedBlockchainAction(blockchain, wallet)
        step = OfflineBroadcastStep.Confirm
        emitState()
    }

    // Single entry point for the confirm screen's primary button. The button's meaning follows the
    // current action, so the ViewModel — not the UI — decides whether to enable or broadcast.
    fun onPrimaryAction() {
        when (confirmAction) {
            is OfflineBroadcastConfirmAction.EnableNetwork -> onEnableNetwork()
            is OfflineBroadcastConfirmAction.PreparingNetwork -> Unit
            OfflineBroadcastConfirmAction.Send -> onBroadcast()
        }
    }

    // Enables the network whose wallet is missing, waits until the wallet and its broadcast adapter
    // are ready, then arms the explicit Send action. It never auto-broadcasts.
    private fun onEnableNetwork() {
        if (confirmAction is OfflineBroadcastConfirmAction.PreparingNetwork) return
        val blockchain = selectedBlockchain ?: return
        val token = tokenToEnable ?: return

        confirmAction = OfflineBroadcastConfirmAction.PreparingNetwork(blockchain.name)
        emitState()

        viewModelScope.launch {
            val wallet = enableWalletAndAwaitAdapter(token, blockchain.type)
            if (wallet == null) {
                confirmAction = OfflineBroadcastConfirmAction.EnableNetwork(blockchain.name)
                errorMessage = Translator.getString(
                    R.string.offline_broadcast_enable_network_failed,
                    blockchain.name,
                )
                emitState()
                return@launch
            }
            targetWallet = wallet
            savePendingImport(wallet)
            confirmAction = OfflineBroadcastConfirmAction.Send
            emitState()
        }
    }

    private suspend fun enableWalletAndAwaitAdapter(token: Token, type: BlockchainType): Wallet? {
        val account = accountManager.activeAccount ?: return null
        try {
            walletUseCase.createWallets(setOf(token))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Creation can fail or only partially succeed (e.g. a hardware enable that derives some
            // keys), so fall through: the wallet/adapter lookup below is authoritative and decides
            // whether the network became usable.
        }

        // Hardware enables persist the wallet asynchronously, so a freshly derived wallet may surface a
        // moment later; bound the wait so it cannot strand the UI in "Preparing". Other account types
        // persist synchronously, so the lookup is already authoritative and an absent wallet means the
        // enable failed — fail fast instead of waiting on a wallet that will never appear.
        if (account.isHardwareWalletAccount && walletFor(type) == null) {
            withTimeoutOrNull(WALLET_WAIT_TIMEOUT_MS) {
                walletUseCase.awaitWallets(setOf(token))
            }
        }
        val wallet = walletFor(type) ?: return null
        return wallet.takeIf { awaitOfflineBroadcastAdapter(it) != null }
    }

    // awaitAdapterForWallet's type parameter is erased, so requesting it as OfflineBroadcastAdapter
    // cannot actually verify the adapter type and would let a non-broadcast adapter through, crashing
    // at the call site. Await the wallet's adapter as its real base type, then perform a genuine
    // checked cast: a non-broadcast adapter yields null instead of crashing.
    private suspend fun awaitOfflineBroadcastAdapter(wallet: Wallet): OfflineBroadcastAdapter? =
        adapterManager.awaitAdapterForWallet<IAdapter>(wallet, ADAPTER_WAIT_TIMEOUT_MS) as? OfflineBroadcastAdapter

    private fun onBroadcast() {
        // A second tap while the first broadcast is still running could re-attempt the send and roll a
        // freshly broadcasted record back to pending, so ignore re-entrant taps.
        if (broadcasting) return
        if (rejectWatchOnlyIfUnsupported(selectedBlockchain)) return
        val wallet = targetWallet
        if (wallet == null) {
            selectedBlockchain?.let { showUnsupportedBlockchainError(it.name) }
                ?: dismissWithError(R.string.offline_broadcast_no_wallet)
            return
        }
        broadcasting = true
        emitState()
        viewModelScope.launch {
            // Wait for the imported record to be persisted so the broadcast status UPDATEs target an
            // existing row.
            importJob?.join()
            // The wallet may be active while its adapter is still starting, so wait instead of
            // treating a not-yet-created adapter as an unsupported blockchain.
            val adapter = awaitOfflineBroadcastAdapter(wallet)
            if (adapter == null) {
                broadcasting = false
                showUnsupportedBlockchainError(wallet.token.blockchain.name)
                return@launch
            }
            result = broadcast(wallet, adapter)
            broadcasting = false
            step = OfflineBroadcastStep.Result
            emitState()
        }
    }

    private suspend fun broadcast(wallet: Wallet, adapter: OfflineBroadcastAdapter): OfflineBroadcastResult {
        val networkName = wallet.token.blockchain.name
        return try {
            offlineRecordKey?.let {
                offlineSignedTransactionRepository.markBroadcastAttempt(it.accountId, it.txHash)
            }
            val broadcastResult = withContext(dispatcherProvider.io) {
                adapter.broadcastRawTransaction(rawHex)
            }
            val queued = broadcastResult.status == BroadcastRawTransactionStatus.Queued
            val recordKey = offlineRecordKey
            // Only an actually submitted transaction is "broadcasted". A queued result means the P2P
            // send did not go through and the kit only accepted it for later retry, so the record stays
            // Pending ("awaiting send") instead of falsely reporting "Sent". Once the queued transaction
            // reaches the network it surfaces in wallet history and the reconciliation in
            // OfflineSignedTransactionsViewModel promotes it to Broadcasted.
            if (recordKey == null) {
                persistRawBroadcast(wallet, broadcastResult.txHash, queued)
            } else if (!queued) {
                recordKey.let {
                    // The adapter derives the real txid from the raw bytes, so reconcile the record to
                    // it and drop the imported payload's claimed hash.
                    offlineSignedTransactionRepository.markBroadcasted(
                        it.accountId,
                        it.txHash,
                        broadcastResult.txHash.ifBlank { it.txHash },
                    )
                }
            }
            OfflineBroadcastResult.Success(
                networkName = networkName,
                txHash = broadcastResult.txHash,
                queued = queued,
                explorerUrl = transactionUrl(wallet, broadcastResult.txHash),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val message = e.offlineBroadcastErrorMessage(wallet.token.coin.code)
            offlineRecordKey?.let {
                offlineSignedTransactionRepository.markBroadcastFailed(it.accountId, it.txHash, message)
            }
            OfflineBroadcastResult.Error(
                networkName = networkName,
                rawHex = rawHex,
                message = message,
            )
        }
    }

    private suspend fun persistRawBroadcast(wallet: Wallet, txHash: String, queued: Boolean) {
        if (txHash.isBlank()) return
        val recordKey = OfflineRecordKey(wallet.account.id, txHash)
        offlineRecordKey = recordKey
        offlineSignedTransactionRepository.saveRawImported(
            wallet = wallet,
            rawHex = rawHex,
            txHash = txHash,
        )
        offlineSignedTransactionRepository.markBroadcastAttempt(recordKey.accountId, recordKey.txHash)
        if (!queued) {
            offlineSignedTransactionRepository.markBroadcasted(
                recordKey.accountId,
                recordKey.txHash,
                recordKey.txHash,
            )
        }
    }

    fun onRetry() {
        result = null
        step = OfflineBroadcastStep.Confirm
        onBroadcast()
    }

    fun onDismissErrorShown() {
        dismissError = null
        emitState()
    }

    fun onErrorMessageShown() {
        errorMessage = null
        emitState()
    }

    // Returns true when back was consumed by stepping from the network picker back to confirm.
    // Confirm and Result have nothing before them, so the host pops the screen.
    fun onBack(): Boolean =
        if (step == OfflineBroadcastStep.SelectBlockchain) {
            step = OfflineBroadcastStep.Confirm
            emitState()
            true
        } else {
            false
    }

    private fun prepareFromDecoded(decoded: DecodedOfflineTransaction, payload: String) {
        val blockchain = marketKit.blockchain(decoded.blockchainUid)
        val account = accountManager.activeAccount
        // Gate on broadcast capability before arming Send: a crafted payload may target a blockchain
        // whose active wallet cannot relay (e.g. an EVM chain), and proceeding would later cast a
        // non-broadcast adapter and crash. The resolver returning a token proves the chain can relay.
        if (blockchain == null || account == null) {
            dismissWithError(R.string.offline_broadcast_no_wallet)
            return
        }
        if (rejectWatchOnlyIfUnsupported(blockchain)) return
        if (!canRelay(blockchain.type, account)) {
            dismissWithError(R.string.offline_broadcast_no_wallet)
            return
        }
        rawHex = decoded.rawHex
        networkSelectable = false
        selectedBlockchain = blockchain

        val wallet = walletFor(blockchain.type)
        if (wallet != null) {
            prepareReadyToSend(wallet, decoded, payload)
        } else {
            prepareEnableNetwork(blockchain, decoded, payload)
        }
    }

    private fun prepareReadyToSend(wallet: Wallet, decoded: DecodedOfflineTransaction, payload: String) {
        targetWallet = wallet
        confirmAction = OfflineBroadcastConfirmAction.Send
        // Persist the imported record through the same path as the enable flow, and keep the job so
        // broadcasting waits for the insert: arming Send while the insert is still in flight would
        // let the best-effort status UPDATEs miss the not-yet-saved row.
        pendingImport = PendingImport(decoded, payload)
        offlineRecordKey = null
        importJob = viewModelScope.launch { savePendingImport(wallet) }
        step = OfflineBroadcastStep.Confirm
        emitState()
    }

    private fun prepareEnableNetwork(blockchain: Blockchain, decoded: DecodedOfflineTransaction, payload: String) {
        val account = accountManager.activeAccount
        val token = account?.let {
            offlineBroadcastTokenResolver.resolveTokenToEnable(blockchain.type, it)
        }
        if (token == null) {
            // No active account, unsupported blockchain, or account type that cannot build a
            // broadcasting adapter: do not offer the enable action.
            dismissWithError(R.string.offline_broadcast_no_wallet)
            return
        }
        tokenToEnable = token
        // The repository requires wallet metadata, so keep the scanned record in memory and persist
        // it only once the wallet exists.
        pendingImport = PendingImport(decoded, payload)
        offlineRecordKey = null
        confirmAction = OfflineBroadcastConfirmAction.EnableNetwork(blockchain.name)
        step = OfflineBroadcastStep.Confirm
        emitState()
    }

    private suspend fun savePendingImport(wallet: Wallet) {
        val pending = pendingImport ?: return
        offlineRecordKey = OfflineRecordKey(wallet.account.id, pending.decoded.txHash)
        offlineSignedTransactionRepository.saveImported(wallet, pending.decoded, pending.payload)
        pendingImport = null
    }

    private fun prepareFromRawHex(text: String) {
        val normalized = text.lowercase()
        if (!OfflineTransactionPayloadEncoder.isRawTransactionHex(normalized)) {
            dismissWithError(R.string.offline_broadcast_invalid_input)
            return
        }
        selectableBlockchains = supportedBlockchains()
        if (selectableBlockchains.isEmpty()) {
            dismissWithError(R.string.offline_broadcast_no_wallet)
            return
        }
        rawHex = normalized
        networkSelectable = true
        offlineRecordKey = null
        step = OfflineBroadcastStep.Confirm
        emitState()
    }

    private fun transactionUrl(wallet: Wallet, txHash: String): String? =
        (adapterManager.getAdapterForWalletOld(wallet) as? ITransactionsAdapter)
            ?.getTransactionUrl(txHash)
            ?.takeIf { it.isNotBlank() }

    private fun walletFor(type: BlockchainType): Wallet? {
        val wallets = walletManager.activeWallets.filter { it.token.blockchainType == type }
        return wallets.firstOrNull { it.token.type is TokenType.Native } ?: wallets.firstOrNull()
    }

    private fun supportedBlockchains(): List<Blockchain> {
        val accountType = accountManager.activeAccount?.type ?: return emptyList()
        val tokenQueries = BlockchainType.supported
            .filter { it.supports(accountType) }
            .flatMap { it.nativeTokenQueries }

        return marketKit.tokens(tokenQueries)
            .filter { it.supports(accountType) && it.blockchainType.supports(accountType) }
            .map { it.blockchain }
            .distinctBy { it.uid }
            .sortedBy { it.type.order }
    }

    private fun selectedBlockchainAction(blockchain: Blockchain, wallet: Wallet?): OfflineBroadcastConfirmAction {
        if (wallet != null) return OfflineBroadcastConfirmAction.Send
        val account = accountManager.activeAccount ?: return OfflineBroadcastConfirmAction.Send
        val token = offlineBroadcastTokenResolver.resolveTokenToEnable(blockchain.type, account)
            ?: return OfflineBroadcastConfirmAction.Send
        tokenToEnable = token
        return OfflineBroadcastConfirmAction.EnableNetwork(blockchain.name)
    }

    private fun rejectWatchOnlyIfUnsupported(blockchain: Blockchain?): Boolean {
        val account = accountManager.activeAccount ?: return false
        val type = blockchain?.type ?: return false
        if (!account.isWatchAccount || canRelay(type, account)) return false

        dismissWithError(R.string.offline_broadcast_watch_only_unavailable)
        return true
    }

    private fun canRelay(type: BlockchainType, account: Account): Boolean =
        offlineBroadcastTokenResolver.resolveTokenToEnable(type, account) != null

    private fun showUnsupportedBlockchainError(blockchainName: String) {
        val message = Translator.getString(R.string.offline_broadcast_unsupported_blockchain)
        offlineRecordKey?.let {
            viewModelScope.launch {
                offlineSignedTransactionRepository.markBroadcastFailed(it.accountId, it.txHash, message)
            }
        }
        result = OfflineBroadcastResult.Error(
            networkName = blockchainName,
            rawHex = rawHex,
            message = message,
        )
        step = OfflineBroadcastStep.Result
        emitState()
    }

    private fun dismissWithError(messageRes: Int) {
        dismissError = Translator.getString(messageRes)
        emitState()
    }

    companion object {
        private const val WALLET_WAIT_TIMEOUT_MS = 10_000L
        private const val ADAPTER_WAIT_TIMEOUT_MS = 10_000L
    }
}

private data class OfflineRecordKey(
    val accountId: String,
    val txHash: String,
)

private data class PendingImport(
    val decoded: DecodedOfflineTransaction,
    val payload: String,
)

enum class OfflineBroadcastStep { Loading, Confirm, SelectBlockchain, Result }

sealed interface OfflineBroadcastConfirmAction {
    data object Send : OfflineBroadcastConfirmAction
    data class EnableNetwork(val blockchainName: String) : OfflineBroadcastConfirmAction
    data class PreparingNetwork(val blockchainName: String) : OfflineBroadcastConfirmAction
}

data class OfflineBroadcastConfirm(
    val rawHex: String,
    val selectable: Boolean,
    val blockchainName: String?,
    val action: OfflineBroadcastConfirmAction = OfflineBroadcastConfirmAction.Send,
) {
    val canBroadcast: Boolean
        get() = blockchainName != null

    // Network name to show in the enable warning; null hides the warning block.
    val enableNetworkName: String?
        get() = when (action) {
            is OfflineBroadcastConfirmAction.EnableNetwork -> action.blockchainName
            is OfflineBroadcastConfirmAction.PreparingNetwork -> action.blockchainName
            OfflineBroadcastConfirmAction.Send -> null
        }
}

sealed interface OfflineBroadcastResult {
    val networkName: String

    data class Success(
        override val networkName: String,
        val txHash: String,
        val queued: Boolean,
        val explorerUrl: String?,
    ) : OfflineBroadcastResult

    data class Error(
        override val networkName: String,
        val rawHex: String,
        val message: String,
    ) : OfflineBroadcastResult
}

internal fun Throwable.offlineBroadcastErrorMessage(feeCoinCode: String): String =
    offlineBroadcastErrorText(feeCoinCode).toString()

internal fun Throwable.offlineBroadcastErrorText(feeCoinCode: String): TranslatableString {
    val error = convertedError
    return error.typedOfflineBroadcastErrorText(feeCoinCode)
        ?: error.message?.knownOfflineBroadcastErrorText(feeCoinCode)
        ?: TranslatableString.ResString(R.string.offline_broadcast_error_send_failed)
}

private fun Throwable.typedOfflineBroadcastErrorText(feeCoinCode: String): TranslatableString? =
    when (this) {
        is LocalizedException -> toResString()
        is UnknownHostException,
        is ConnectException,
        is NoRouteToHostException -> TranslatableString.ResString(R.string.Hud_Text_NoInternet)
        is InterruptedIOException,
        is TimeoutException -> TranslatableString.ResString(R.string.offline_broadcast_error_timeout)
        is UnsupportedException,
        is UnsupportedOperationException -> TranslatableString.ResString(R.string.offline_broadcast_unsupported_blockchain)
        is EvmError.InsufficientBalanceWithFee -> TranslatableString.ResString(
            R.string.EthereumTransaction_Error_InsufficientBalanceWithFee,
            feeCoinCode
        )
        is EvmError.CannotEstimateSwap -> TranslatableString.ResString(
            R.string.EthereumTransaction_Error_CannotEstimate,
            feeCoinCode
        )
        is EvmError.LowerThanBaseGasLimit -> TranslatableString.ResString(
            R.string.EthereumTransaction_Error_LowerThanBaseGasLimit
        )
        is EvmError.ExecutionReverted -> TranslatableString.ResString(
            R.string.EthereumTransaction_Error_ExecutionReverted,
            message.orEmpty()
        )
        is EvmError.InsufficientLiquidity -> TranslatableString.ResString(
            R.string.EthereumTransaction_Error_InsufficientLiquidity
        )
        is EvmError.BlockedByProvider -> TranslatableString.ResString(R.string.source_blocked_by_provider_error)
        is EvmError.RpcError -> message?.knownOfflineBroadcastErrorText(feeCoinCode)
            ?: TranslatableString.ResString(R.string.offline_broadcast_error_send_failed)
        else -> null
    }

private fun String.knownOfflineBroadcastErrorText(feeCoinCode: String): TranslatableString? {
    val message = lowercase()
    return when {
        message.isBlank() || message == "error" -> TranslatableString.ResString(
            R.string.offline_broadcast_error_send_failed
        )
        message.containsAny(alreadyKnownTransactionMessages) -> TranslatableString.ResString(
            R.string.offline_broadcast_error_already_sent
        )
        message.contains("nonce too low") -> TranslatableString.ResString(
            R.string.offline_broadcast_error_nonce_used
        )
        message.containsAny(lowFeeMessages) -> TranslatableString.ResString(
            R.string.offline_broadcast_error_low_fee
        )
        message.contains("insufficient funds") -> TranslatableString.ResString(
            R.string.EthereumTransaction_Error_InsufficientBalanceWithFee,
            feeCoinCode
        )
        message.containsAny(rejectedTransactionMessages) -> TranslatableString.ResString(
            R.string.offline_broadcast_error_rejected
        )
        else -> null
    }
}

private fun String.containsAny(messages: List<String>): Boolean =
    messages.any { contains(it) }

private val alreadyKnownTransactionMessages = listOf(
    "already known",
    "already imported",
    "known transaction",
)

private val lowFeeMessages = listOf(
    "transaction underpriced",
    "replacement transaction underpriced",
    "fee too low",
    "insufficient fee",
)

private val rejectedTransactionMessages = listOf(
    "invalid sender",
    "intrinsic gas too low",
    "exceeds block gas limit",
    "chain id",
)

data class OfflineBroadcastUiState(
    val step: OfflineBroadcastStep,
    val confirm: OfflineBroadcastConfirm?,
    val selectableBlockchains: List<Blockchain>,
    val selectedBlockchain: Blockchain?,
    val broadcasting: Boolean,
    val result: OfflineBroadcastResult?,
    val dismissError: String?,
    val errorMessage: String?,
)
