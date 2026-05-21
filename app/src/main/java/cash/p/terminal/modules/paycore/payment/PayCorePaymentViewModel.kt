package cash.p.terminal.modules.paycore.payment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.paycore.PAYCORE_COMPLETE_BACK_URL
import cash.p.terminal.modules.paycore.PayCoreApiService
import cash.p.terminal.modules.paycore.PayCorePaymentCreateRequest
import cash.p.terminal.modules.paycore.payCoreUserMessage
import cash.p.terminal.modules.paycore.transactionIdOrNull
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal

data class PayCorePaymentParams(
    val amountIn: BigDecimal,
    val amountOut: BigDecimal,
    val networkType: String,
    val tokenInUid: String,
    val tokenOutUid: String,
    val blockchainTypeIn: String,
    val blockchainTypeOut: String,
    val addressOut: String,
)

class PayCorePaymentViewModel(
    private val apiService: PayCoreApiService,
    private val storage: SwapProviderTransactionsStorage,
    private val accountManager: IAccountManager,
    private val params: PayCorePaymentParams,
) : ViewModel() {

    val amountIn: BigDecimal get() = params.amountIn
    val amountOut: BigDecimal get() = params.amountOut
    val networkType: String get() = params.networkType

    var uiState by mutableStateOf(PayCorePaymentUiState())
        private set

    fun onConfirm() {
        uiState = uiState.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                apiService.createPayment(
                    PayCorePaymentCreateRequest(
                        amount = params.amountIn.toPlainString(),
                        networkType = params.networkType,
                        backUrl = PAYCORE_COMPLETE_BACK_URL
                    )
                )
            }.fold(
                onSuccess = { response ->
                    Timber.d(
                        "PayCore payment created: id=%s, url=%s",
                        response.transactionIdOrNull(),
                        response.url
                    )
                    uiState = uiState.copy(
                        loading = false,
                        paymentId = response.transactionIdOrNull(),
                        paymentUrl = response.url
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        loading = false,
                        error = error.payCoreUserMessage()
                    )
                }
            )
        }
    }

    fun onWebViewCompleted() {
        val paymentId = uiState.paymentId ?: return
        val accountId = accountManager.activeAccount?.id ?: return
        storage.save(
            SwapProviderTransaction(
                outgoingRecordUid = null,
                transactionId = paymentId,
                status = "waiting",
                provider = SwapProvider.PAYCORE,
                coinUidIn = params.tokenInUid,
                blockchainTypeIn = params.blockchainTypeIn,
                amountIn = params.amountIn,
                addressIn = "",
                coinUidOut = params.tokenOutUid,
                blockchainTypeOut = params.blockchainTypeOut,
                amountOut = params.amountOut,
                addressOut = params.addressOut,
                accountId = accountId,
            )
        )
        uiState = uiState.copy(completed = true)
    }
}

data class PayCorePaymentUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val paymentId: String? = null,
    val paymentUrl: String? = null,
    val completed: Boolean = false,
)
