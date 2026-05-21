package cash.p.terminal.modules.paycore.verification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.paycore.PAYCORE_COMPLETE_BACK_URL
import cash.p.terminal.modules.paycore.PayCoreApiService
import cash.p.terminal.modules.paycore.PayCoreLinkedWallet
import cash.p.terminal.modules.paycore.PayCoreSecureStorage
import cash.p.terminal.modules.paycore.PayCoreSecureStorage.VerificationStatus
import cash.p.terminal.modules.paycore.PayCoreSignatureHelper
import cash.p.terminal.modules.paycore.PayCoreWalletChangeRequest
import cash.p.terminal.modules.paycore.PayCoreWalletCreateRequest
import cash.p.terminal.modules.paycore.PayCoreWalletCreateResponse
import cash.p.terminal.modules.paycore.payCoreUserMessage
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.launch

class PayCoreVerificationViewModel(
    private val networkType: String,
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
            runCatching { createWallet(fullPhone) }
                .fold(
                    onSuccess = { response -> handleCreateWalletSuccess(response, fullPhone, presentation) },
                    onFailure = { error -> handleCreateWalletFailure(error, presentation) }
                )
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
        uiState = uiState.copy(
            screen = VerificationScreen.PhoneInput,
            loading = false,
            error = null,
            supportRequired = false
        )
    }

    private suspend fun createWallet(fullPhone: String): PayCoreWalletCreateResponse {
        val address = signatureHelper.getWalletAddress(networkType)
        val verifySignKey = signatureHelper.signPhone(fullPhone)
        return apiService.createWallet(
            PayCoreWalletCreateRequest(
                phone = fullPhone,
                address = address,
                networkType = networkType,
                verifySignKey = verifySignKey,
                backUrl = PAYCORE_COMPLETE_BACK_URL
            )
        )
    }

    private suspend fun handleCreateWalletSuccess(
        response: PayCoreWalletCreateResponse,
        fullPhone: String,
        presentation: VerificationRequestPresentation
    ) {
        secureStorage.setPhone(fullPhone)
        when (response.status) {
            0 -> {
                saveLinkedWalletForActiveAccount()
                uiState = uiState.copy(
                    screen = VerificationScreen.KycRequired,
                    kycUrl = response.url,
                    loading = false
                )
            }
            1 -> recoverLinkedWallet(fullPhone, presentation)
            2 -> {
                saveLinkedWalletForActiveAccount()
                uiState = uiState.copy(
                    screen = VerificationScreen.VerificationWarning,
                    loading = false
                )
            }
            else -> uiState = verificationErrorState("Unknown status: ${response.status}", presentation)
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

        val newAddress = signatureHelper.getWalletAddress(networkType)
        val changeResult = runCatching {
            apiService.changeWallet(
                request = PayCoreWalletChangeRequest(
                    address = newAddress,
                    networkType = networkType
                ),
                signingAccount = oldAccount
            )
        }
        if (changeResult.isFailure) {
            uiState = supportRequiredState(presentation)
            return
        }

        runCatching { createWallet(fullPhone) }
            .fold(
                onSuccess = { retryResponse ->
                    if (retryResponse.status == 1) {
                        uiState = supportRequiredState(presentation)
                    } else {
                        handleCreateWalletSuccess(retryResponse, fullPhone, presentation)
                    }
                },
                onFailure = { uiState = supportRequiredState(presentation) }
            )
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
