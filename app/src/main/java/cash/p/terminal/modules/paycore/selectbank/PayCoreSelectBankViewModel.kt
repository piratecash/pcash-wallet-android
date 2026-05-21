package cash.p.terminal.modules.paycore.selectbank

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase
import cash.p.terminal.modules.paycore.PAYCORE_COMPLETE_BACK_URL
import cash.p.terminal.modules.paycore.PayCoreApiService
import cash.p.terminal.modules.paycore.PayCoreNetworkMapper
import cash.p.terminal.modules.paycore.PayCorePayoutProcessRequest
import cash.p.terminal.modules.paycore.payCoreUserMessage
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.changenow.domain.entity.toStatus
import cash.p.terminal.strings.helpers.Translator
import kotlinx.coroutines.launch
import timber.log.Timber

class PayCoreSelectBankViewModel(
    private val apiService: PayCoreApiService,
    private val storage: SwapProviderTransactionsStorage,
    private val updateStatus: UpdateSwapProviderTransactionsStatusUseCase,
) : ViewModel() {

    var uiState by mutableStateOf(PayCoreSelectBankUiState())
        private set

    fun onSelectBankClick(transactionId: String) {
        if (uiState.loading || uiState.webViewUrl != null) return
        uiState = uiState.copy(
            loading = true,
            error = null,
            activeTransactionId = transactionId,
        )
        viewModelScope.launch {
            runCatching { requestPayoutUrl(transactionId) }.fold(
                onSuccess = { result ->
                    uiState = when (result) {
                        is PayoutRequestResult.Success ->
                            uiState.copy(loading = false, webViewUrl = result.url)
                        PayoutRequestResult.Stale ->
                            uiState.copy(loading = false, activeTransactionId = null)
                        PayoutRequestResult.EmptyUrl ->
                            uiState.copy(
                                loading = false,
                                error = defaultError(),
                                activeTransactionId = null,
                            )
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "PayCore select bank failed")
                    uiState = uiState.copy(
                        loading = false,
                        error = error.payCoreUserMessage(defaultError()),
                        activeTransactionId = null,
                    )
                }
            )
        }
    }

    fun onWebViewClosed() {
        // Status refresh must happen for the transaction whose WebView was open,
        // not whatever the caller is showing right now.
        val transactionId = uiState.activeTransactionId
        // Keep `loading` true until status refresh finishes so the user cannot
        // re-trigger processPayOut while the DB row is being updated.
        uiState = uiState.copy(webViewUrl = null, loading = transactionId != null)
        if (transactionId == null) return
        viewModelScope.launch {
            runCatching { updateStatus.updateTransactionStatus(transactionId) }
                .onFailure { Timber.e(it, "PayCore status refresh failed") }
            uiState = uiState.copy(loading = false, activeTransactionId = null)
        }
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    private suspend fun requestPayoutUrl(transactionId: String): PayoutRequestResult {
        val tx = storage.getTransaction(transactionId)
            ?: error("PayCore transaction not found: $transactionId")
        // Guard against late clicks: the row may have moved out of
        // CREATED_OR_WAIT_USER between the click and the DB observer propagating.
        if (tx.status.toStatus() != TransactionStatusEnum.CREATED_OR_WAIT_USER) {
            return PayoutRequestResult.Stale
        }
        val txHash = tx.outgoingRecordUid?.takeIf(String::isNotBlank)
            ?: error("PayCore transaction has no outgoing record uid")
        val networkType = requireNotNull(PayCoreNetworkMapper.fromBlockchainTypeUid(tx.blockchainTypeIn)) {
            "Unsupported PayCore payout blockchain type: ${tx.blockchainTypeIn}"
        }
        val url = apiService.processPayOut(
            request = PayCorePayoutProcessRequest(
                transactionHash = txHash,
                backUrl = PAYCORE_COMPLETE_BACK_URL,
            ),
            networkType = networkType,
        ).url
        return if (url.isNullOrBlank()) PayoutRequestResult.EmptyUrl else PayoutRequestResult.Success(url)
    }

    private fun defaultError(): String =
        Translator.getString(R.string.paycore_select_bank_error)

    private sealed interface PayoutRequestResult {
        data class Success(val url: String) : PayoutRequestResult
        data object Stale : PayoutRequestResult
        data object EmptyUrl : PayoutRequestResult
    }
}

data class PayCoreSelectBankUiState(
    val loading: Boolean = false,
    val webViewUrl: String? = null,
    val error: String? = null,
    val activeTransactionId: String? = null,
)
