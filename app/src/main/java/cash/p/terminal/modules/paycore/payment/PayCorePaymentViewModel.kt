package cash.p.terminal.modules.paycore.payment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.paycore.PayCoreApiService
import cash.p.terminal.modules.paycore.PayCoreAmountType
import cash.p.terminal.modules.paycore.PayCoreTicker
import cash.p.terminal.modules.paycore.PayCorePaymentCalculationRequest
import cash.p.terminal.modules.paycore.PayCorePaymentCreateRequest
import cash.p.terminal.modules.paycore.PayCoreWalletApprovalService
import cash.p.terminal.modules.paycore.payCoreUserMessage
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.parseIsoTimestamp
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException

data class PayCorePaymentParams(
    val amountIn: BigDecimal,
    val amountOut: BigDecimal,
    val networkType: PayCoreTicker,
    val tokenInUid: String,
    val tokenOutUid: String,
    val blockchainTypeIn: String,
    val blockchainTypeOut: String,
    val addressOut: String,
)

class PayCorePaymentViewModel(
    private val apiService: PayCoreApiService,
    private val walletApprovalService: PayCoreWalletApprovalService,
    private val storage: SwapProviderTransactionsStorage,
    private val accountManager: IAccountManager,
    private val params: PayCorePaymentParams,
) : ViewModel() {

    private var calculationCreatedAtMillis: Long? = null
    private var savedPaymentId: String? = null
    private var paymentTransactionDate: Long? = null
    private var paymentCompletionPending = false

    val amountIn: BigDecimal get() = uiState.amountIn ?: params.amountIn
    val amountOut: BigDecimal get() = uiState.amountOut ?: params.amountOut
    val networkType: PayCoreTicker get() = params.networkType

    var uiState by mutableStateOf(PayCorePaymentUiState())
        private set

    init {
        calculatePayment()
    }

    fun onConfirm() {
        if (uiState.loading) return

        if (paymentCompletionPending) {
            retryPendingPaymentCompletion()
            return
        }

        if (uiState.paymentId != null && uiState.paymentUrl != null) {
            if (hasFreshPayment()) {
                uiState = uiState.copy(showWebView = true, error = null)
                return
            }
            clearPayment()
        }

        val calculationUuid = uiState.calculationUuid
        if (calculationUuid == null || !hasFreshCalculation()) {
            calculatePayment(createPaymentAfterCalculation = true)
            return
        }

        createPayment(calculationUuid)
    }

    private fun createPayment(calculationUuid: String) {
        uiState = uiState.copy(loading = true, error = null)
        viewModelScope.launch {
            createPaymentSafely(calculationUuid)
        }
    }

    private suspend fun createPaymentSafely(calculationUuid: String) {
        try {
            val response = apiService.createPayment(
                request = PayCorePaymentCreateRequest(uuid = calculationUuid),
                networkType = params.networkType,
            )
            Timber.d(
                "PayCore payment created: id=%s, url=%s",
                response.uuid,
                response.paymentUrl
            )
            val paymentId = response.uuid.takeIf(String::isNotBlank)
            val paymentUrl = response.paymentUrl.takeIf(String::isNotBlank)
            if (paymentId == null || paymentUrl == null) {
                uiState = uiState.copy(
                    loading = false,
                    error = Translator.getString(R.string.Error)
                )
                return
            }
            if (paymentId != uiState.paymentId) {
                savedPaymentId = null
                paymentTransactionDate = null
                paymentCompletionPending = false
            }
            uiState = uiState.copy(
                loading = false,
                paymentId = paymentId,
                paymentUrl = paymentUrl,
                paymentExpiresAt = response.expiresAt,
                showWebView = true,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            uiState = uiState.copy(
                loading = false,
                error = error.payCoreUserMessage()
            )
        }
    }

    private fun calculatePayment(createPaymentAfterCalculation: Boolean = false) {
        if (uiState.loading) return

        uiState = uiState.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                walletApprovalService.ensureApprovedForSavedPhone(
                    walletAddress = params.addressOut,
                    networkType = params.networkType,
                )
                val response = apiService.calculatePayment(
                    request = PayCorePaymentCalculationRequest(
                        amount = params.amountIn,
                        amountType = PayCoreAmountType.RUB,
                        ticker = params.networkType,
                    ),
                    networkType = params.networkType,
                )
                calculationCreatedAtMillis = System.currentTimeMillis()
                uiState = uiState.copy(
                    loading = createPaymentAfterCalculation,
                    amountIn = response.fullAmountRub,
                    amountOut = response.amountCrypto,
                    calculationUuid = response.uuid,
                    calculationExpiresAt = response.expiresAt,
                    error = null
                )
                if (createPaymentAfterCalculation) {
                    createPaymentSafely(response.uuid)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                calculationCreatedAtMillis = null
                uiState = uiState.copy(
                    loading = false,
                    calculationUuid = null,
                    calculationExpiresAt = null,
                    error = error.payCoreUserMessage()
                )
            }
        }
    }

    private fun hasFreshCalculation(): Boolean {
        val createdAt = calculationCreatedAtMillis ?: return false
        return System.currentTimeMillis() - createdAt < PAYMENT_CALCULATION_REFRESH_MS
    }

    private fun hasFreshPayment(): Boolean {
        val expiresAt = uiState.paymentExpiresAt?.parseIsoTimestamp() ?: return false
        return System.currentTimeMillis() + PAYMENT_EXPIRATION_SAFETY_MARGIN_MS < expiresAt
    }

    private fun clearPayment() {
        calculationCreatedAtMillis = null
        savedPaymentId = null
        paymentTransactionDate = null
        paymentCompletionPending = false
        uiState = uiState.copy(
            calculationUuid = null,
            calculationExpiresAt = null,
            paymentId = null,
            paymentUrl = null,
            paymentExpiresAt = null,
            showWebView = false,
        )
    }

    fun onWebViewOpened() {
        val paymentId = uiState.paymentId ?: return
        if (savedPaymentId == paymentId) return

        viewModelScope.launch {
            savePaymentTransaction()
        }
    }

    fun onWebViewClosed() {
        uiState = uiState.copy(showWebView = false)
    }

    fun onWebViewCompleted() {
        paymentCompletionPending = true
        uiState = uiState.copy(loading = true, showWebView = false, error = null)
        viewModelScope.launch {
            completePaymentAfterSave()
        }
    }

    private fun retryPendingPaymentCompletion() {
        uiState = uiState.copy(loading = true, error = null)
        viewModelScope.launch {
            completePaymentAfterSave()
        }
    }

    private suspend fun completePaymentAfterSave() {
        if (savePaymentTransaction()) {
            paymentCompletionPending = false
            uiState = uiState.copy(
                loading = false,
                showWebView = false,
                completed = true,
                error = null,
            )
        } else {
            uiState = uiState.copy(
                loading = false,
                showWebView = false,
                error = Translator.getString(R.string.Error),
            )
        }
    }

    private suspend fun savePaymentTransaction(): Boolean {
        val paymentId = uiState.paymentId ?: return false
        if (savedPaymentId == paymentId) return true

        val accountId = accountManager.activeAccount?.id ?: return false
        val transactionDate = paymentTransactionDate ?: System.currentTimeMillis().also {
            paymentTransactionDate = it
        }
        try {
            storage.saveAsync(
                SwapProviderTransaction(
                    date = transactionDate,
                    outgoingRecordUid = null,
                    transactionId = paymentId,
                    status = TransactionStatusEnum.WAITING.name.lowercase(),
                    provider = SwapProvider.PAYCORE,
                    coinUidIn = params.tokenInUid,
                    blockchainTypeIn = params.blockchainTypeIn,
                    amountIn = amountIn,
                    addressIn = "",
                    coinUidOut = params.tokenOutUid,
                    blockchainTypeOut = params.blockchainTypeOut,
                    amountOut = amountOut,
                    addressOut = params.addressOut,
                    accountId = accountId,
                )
            )
            savedPaymentId = paymentId
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Timber.e(error, "Failed to save PayCore payment transaction")
            return false
        }
    }
}

data class PayCorePaymentUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val amountIn: BigDecimal? = null,
    val amountOut: BigDecimal? = null,
    val calculationUuid: String? = null,
    val calculationExpiresAt: String? = null,
    val paymentId: String? = null,
    val paymentUrl: String? = null,
    val paymentExpiresAt: String? = null,
    val showWebView: Boolean = false,
    val completed: Boolean = false,
)

private const val PAYMENT_CALCULATION_REFRESH_MS = 25_000L
private const val PAYMENT_EXPIRATION_SAFETY_MARGIN_MS = 5_000L
