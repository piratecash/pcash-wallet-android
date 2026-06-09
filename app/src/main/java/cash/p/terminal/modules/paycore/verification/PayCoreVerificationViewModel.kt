package cash.p.terminal.modules.paycore.verification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.isNoInternetException
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.paycore.PayCoreApiService
import cash.p.terminal.modules.paycore.PayCoreLinkedWallet
import cash.p.terminal.modules.paycore.PayCoreTicker
import cash.p.terminal.modules.paycore.PayCoreSecureStorage
import cash.p.terminal.modules.paycore.PayCoreSecureStorage.VerificationStatus
import cash.p.terminal.modules.paycore.PayCoreSignatureHelper
import cash.p.terminal.modules.paycore.PayCoreWalletApprovalResult
import cash.p.terminal.modules.paycore.PayCoreWalletApprovalService
import cash.p.terminal.modules.paycore.PayCoreWalletChangeRequest
import cash.p.terminal.modules.paycore.payCoreUserMessage
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class PayCoreVerificationViewModel(
    private val networkType: PayCoreTicker,
    private val walletAddress: String,
    private val walletApprovalService: PayCoreWalletApprovalService,
    private val apiService: PayCoreApiService,
    private val secureStorage: PayCoreSecureStorage,
    private val signatureHelper: PayCoreSignatureHelper,
    private val accountManager: IAccountManager
) : ViewModel() {

    var uiState by mutableStateOf(PayCoreVerificationUiState())
        private set

    fun onPhoneChange(digits: String) {
        uiState = uiState.copy(
            phone = digits,
            error = null,
            supportRequired = false
        )
    }

    fun onContinueClick() {
        submitPhone(uiState.phone, VerificationRequestPresentation.Inline)
    }

    private fun submitPhone(digits: String, presentation: VerificationRequestPresentation) {
        if (digits.length != 10 || uiState.loading) return

        uiState = uiState.copy(
            screen = if (presentation == VerificationRequestPresentation.Inline) {
                VerificationScreen.PhoneInput
            } else {
                VerificationScreen.Processing
            },
            phone = digits,
            loading = true,
            error = null,
            supportRequired = false
        )

        val fullPhone = "+7$digits"
        viewModelScope.launch {
            try {
                val result = requestApproval(fullPhone)
                handleApprovalResult(result, fullPhone, presentation)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                handleCreateWalletFailure(error, presentation)
            }
        }
    }

    fun onVerificationWarningAccepted() {
        secureStorage.setVerificationStatus(VerificationStatus.VERIFIED)
        uiState = uiState.copy(completed = true)
    }

    fun onKycCompleted() {
        val phone = secureStorage.getPhone() ?: return
        val cleanPhone = phone.removePrefix("+7")
        submitPhone(cleanPhone, VerificationRequestPresentation.FullScreen)
    }

    fun onRetry() {
        if (uiState.screen == VerificationScreen.Processing && uiState.phone.length == 10) {
            submitPhone(uiState.phone, VerificationRequestPresentation.FullScreen)
            return
        }
        uiState = uiState.copy(
            screen = VerificationScreen.PhoneInput,
            loading = false,
            error = null,
            supportRequired = false
        )
    }

    private suspend fun requestApproval(fullPhone: String): PayCoreWalletApprovalResult {
        return walletApprovalService.requestApproval(
            phone = fullPhone,
            walletAddress = walletAddress,
            networkType = networkType,
        )
    }

    private suspend fun handleApprovalResult(
        result: PayCoreWalletApprovalResult,
        fullPhone: String,
        presentation: VerificationRequestPresentation
    ) {
        secureStorage.setPhone(fullPhone)
        when (result) {
            is PayCoreWalletApprovalResult.NotRegistered -> {
                saveLinkedWalletForActiveAccount()
                uiState = uiState.copy(
                    screen = VerificationScreen.KycRequired,
                    kycUrl = result.url,
                    loading = false
                )
            }
            PayCoreWalletApprovalResult.NoAccess -> recoverLinkedWallet(fullPhone, presentation)
            PayCoreWalletApprovalResult.Pending -> {
                uiState = uiState.copy(
                    screen = VerificationScreen.Processing,
                    loading = false
                )
            }
            PayCoreWalletApprovalResult.Approved -> {
                saveLinkedWalletForActiveAccount()
                uiState = uiState.copy(
                    screen = VerificationScreen.VerificationWarning,
                    loading = false
                )
            }
            PayCoreWalletApprovalResult.Rejected,
            PayCoreWalletApprovalResult.Suspended -> {
                uiState = supportRequiredState(presentation)
            }
            PayCoreWalletApprovalResult.MissingPhone,
            PayCoreWalletApprovalResult.MissingWalletAddress -> {
                uiState = verificationErrorState(defaultVerificationError(), presentation)
            }
            is PayCoreWalletApprovalResult.Unknown -> {
                uiState = verificationErrorState(defaultVerificationError(), presentation)
            }
        }
    }

    private suspend fun recoverLinkedWallet(
        fullPhone: String,
        presentation: VerificationRequestPresentation
    ) {
        val oldAccount = findPreviouslyLinkedAccount()
        if (oldAccount == null) {
            uiState = supportRequiredState(presentation)
            return
        }

        try {
            apiService.changeWallet(
                request = PayCoreWalletChangeRequest(
                    address = walletAddress,
                    networkType = networkType
                ),
                signingAccount = oldAccount
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            uiState = recoveryFailureState(error, presentation)
            return
        }

        try {
            val retryResult = requestApproval(fullPhone)
            if (retryResult == PayCoreWalletApprovalResult.NoAccess) {
                uiState = supportRequiredState(presentation)
            } else {
                handleApprovalResult(retryResult, fullPhone, presentation)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            uiState = recoveryFailureState(error, presentation)
        }
    }

    private fun recoveryFailureState(
        error: Throwable,
        presentation: VerificationRequestPresentation
    ): PayCoreVerificationUiState {
        // A transient connectivity failure is recoverable, so surface it as a
        // network error instead of the terminal "support required" state.
        return if (error.isNoInternetException()) {
            verificationErrorState(error.payCoreUserMessage(), presentation)
        } else {
            supportRequiredState(presentation)
        }
    }

    private fun findPreviouslyLinkedAccount(): Account? {
        val linked = secureStorage.getLinkedWallet() ?: return null
        return accountManager.account(linked.accountId)?.takeIf { account ->
            tryDeriveAddress(account)?.equals(linked.address, ignoreCase = true) == true
        }
    }

    private fun tryDeriveAddress(account: Account): String? {
        return tryOrNull { signatureHelper.getWalletAddress(networkType, account) }
    }

    private fun saveLinkedWalletForActiveAccount() {
        val activeAccount = accountManager.activeAccount ?: return
        val address = signatureHelper.getWalletAddress(networkType, activeAccount)
        secureStorage.setLinkedWallet(
            PayCoreLinkedWallet(
                accountId = activeAccount.id,
                address = address,
                networkType = networkType
            )
        )
    }

    private fun handleCreateWalletFailure(
        error: Throwable,
        presentation: VerificationRequestPresentation
    ) {
        uiState = verificationErrorState(
            error.payCoreUserMessage(defaultVerificationError()),
            presentation
        )
    }

    private fun verificationErrorState(
        message: String,
        presentation: VerificationRequestPresentation
    ): PayCoreVerificationUiState {
        return if (presentation == VerificationRequestPresentation.Inline) {
            uiState.copy(
                screen = VerificationScreen.PhoneInput,
                loading = false,
                error = message,
                supportRequired = false
            )
        } else {
            uiState.copy(
                screen = VerificationScreen.Error,
                loading = false,
                error = message,
                supportRequired = false
            )
        }
    }

    private fun supportRequiredState(
        presentation: VerificationRequestPresentation
    ): PayCoreVerificationUiState {
        return uiState.copy(
            screen = if (presentation == VerificationRequestPresentation.Inline) {
                VerificationScreen.PhoneInput
            } else {
                VerificationScreen.Error
            },
            loading = false,
            error = null,
            supportRequired = true
        )
    }

    private fun defaultVerificationError(): String {
        return Translator.getString(R.string.paycore_verification_error)
    }
}

data class PayCoreVerificationUiState(
    val screen: VerificationScreen = VerificationScreen.PhoneInput,
    val phone: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val kycUrl: String? = null,
    val completed: Boolean = false,
    val supportRequired: Boolean = false
)

enum class VerificationScreen { PhoneInput, Processing, KycRequired, VerificationWarning, Error }

private enum class VerificationRequestPresentation {
    Inline,
    FullScreen
}
