package cash.p.terminal.tangem.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.tangem.domain.usecase.BackupHardwareWalletUseCase
import cash.p.terminal.tangem.domain.usecase.ICreateHardwareWalletUseCase
import cash.p.terminal.tangem.domain.usecase.ResetToFactorySettingsUseCase
import cash.p.terminal.tangem.domain.usecase.TangemCreateWalletsUseCase
import cash.p.terminal.tangem.domain.usecase.ValidateBackUseCase
import com.tangem.common.CompletionResult
import com.tangem.common.card.Card
import com.tangem.common.core.TangemSdkError
import com.tangem.common.doOnFailure
import com.tangem.common.doOnSuccess
import com.tangem.operations.attestation.AttestationTask
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class HardwareWalletOnboardingViewModel(
    private val tangemCreateWalletUseCase: TangemCreateWalletsUseCase,
    private val backupHardwareWalletUseCase: BackupHardwareWalletUseCase,
    private val createHardwareWalletUseCase: ICreateHardwareWalletUseCase,
    private val resetToFactorySettingsUseCase: ResetToFactorySettingsUseCase,
    private val validateBackUseCase: ValidateBackUseCase,
    private val connectivityManager: IConnectivityManager
) : ViewModel() {

    private companion object {
        const val MAX_BACKUP_CARDS = 2
    }

    private val _uiState =
        mutableStateOf(HardwareWalletOnboardingUIState())
    val uiState: State<HardwareWalletOnboardingUIState> get() = _uiState

    private val _errorEvents = Channel<HardwareWalletError>(capacity = 1)
    val errorEvents = _errorEvents.receiveAsFlow()

    private var isOfflineModeForced = false
    private var pendingOfflineAction: OfflineAction? = null

    var accountName: String? = null

    init {
        updateActualPage()
    }

    private fun updateActualPage() {
        val card = tangemCreateWalletUseCase.tangemSdkManager.lastScanResponse?.card ?: return
        if (card.wallets.isNotEmpty() &&
            card.backupStatus == Card.BackupStatus.NoBackup
        ) {
            _uiState.value = _uiState.value.copy(
                currentStep = OnboardingStep.ADD_BACKUP
            )
        }
    }

    fun createWallet() {
        runWithNetworkCheck(OfflineAction.CreateWallet) actionBlock@{
            val lastScanResponse = tangemCreateWalletUseCase.tangemSdkManager.lastScanResponse
            if (lastScanResponse != null) {
                tangemCreateWalletUseCase(lastScanResponse, false)
                    .doOnSuccess {
                        _uiState.value = _uiState.value.copy(
                            currentStep = OnboardingStep.ADD_BACKUP
                        )
                    }.doOnFailure {
                        _errorEvents.trySend(HardwareWalletError.WalletsNotCreated)
                    }
            } else {
                _errorEvents.trySend(HardwareWalletError.UnknownError)
            }
        }
    }

    fun onGoToFinalPageClick() {
        _uiState.value = _uiState.value.copy(
            currentStep = OnboardingStep.CREATE_ACCESS_CODE
        )
    }

    fun createBackup() {
        runWithNetworkCheck(OfflineAction.CreateBackup) actionBlock@{
            val primaryCard = tangemCreateWalletUseCase.tangemSdkManager.lastScanResponse?.primaryCard
            if (primaryCard == null) {
                _uiState.value = _uiState.value.copy(
                    currentStep = OnboardingStep.CREATE_WALLET
                )
                return@actionBlock
            }

            backupHardwareWalletUseCase.addBackup(primaryCard)
                .doOnSuccess { card ->
                    _uiState.value = _uiState.value.copy(
                        primaryCardId = primaryCard.cardId,
                        backupCards = _uiState.value.backupCards + card
                    )
                    if (backupHardwareWalletUseCase.addedBackupCardsCount == MAX_BACKUP_CARDS) {
                        _uiState.value = _uiState.value.copy(
                            currentStep = OnboardingStep.CREATE_ACCESS_CODE
                        )
                    }
                }
                .doOnFailure { error ->
                    when (error) {
                        is TangemSdkError.CardVerificationFailed -> {
                            _errorEvents.trySend(HardwareWalletError.AttestationFailed)
                        }

                        is TangemSdkError.BackupFailedNotEmptyWallets -> {
                            _errorEvents.trySend(HardwareWalletError.NeedFactoryReset(error.cardId))
                        }

                        is TangemSdkError.IssuerSignatureLoadingFailed -> {
                            _errorEvents.trySend(HardwareWalletError.AttestationFailed)
                        }

                        else -> {
                            _errorEvents.trySend(HardwareWalletError.UnknownError)
                        }
                    }
                }
        }
    }

    fun setAccessCode(accessCode: String) {
        backupHardwareWalletUseCase.setAccessCode(accessCode)
        _uiState.value = _uiState.value.copy(
            currentStep = OnboardingStep.FINAL
        )
    }

    fun onWriteFinalDataClicked() {
        runWithNetworkCheck(OfflineAction.ProceedBackup) {
            handleBackupResult()
        }
    }

    fun resetCard(cardId: String) {
        runWithNetworkCheck(OfflineAction.ResetPrimaryCard(cardId)) {
            resetToFactorySettingsUseCase.resetPrimaryCard(cardId, false)
        }
    }

    fun onOfflineModeConfirmed() {
        _uiState.value = _uiState.value.copy(
            showOfflineWarningDialog = false
        )
        val action = pendingOfflineAction ?: return
        pendingOfflineAction = null
        isOfflineModeForced = true
        viewModelScope.launch {
            tangemCreateWalletUseCase.tangemSdkManager.setAttestationMode(AttestationTask.Mode.Offline)
            executeOfflineAction(action)
        }
    }

    fun onOfflineModeCancelled() {
        pendingOfflineAction = null
        _uiState.value = _uiState.value.copy(
            showOfflineWarningDialog = false
        )
    }

    private suspend fun handleBackupResult() {
        val result = backupHardwareWalletUseCase.proceedBackup()
        when (result) {
            is CompletionResult.Success -> {
                if (!validateBackUseCase.isValidBackupStatus(result.data)) {
                    _errorEvents.trySend(HardwareWalletError.ErrorInBackupCard)
                }
                val lastScanResponse = tangemCreateWalletUseCase.tangemSdkManager.lastScanResponse
                if (backupHardwareWalletUseCase.isBackupFinished() && lastScanResponse != null && accountName != null) {
                    createHardwareWalletUseCase(
                        accountName = accountName!!,
                        // added backup count to response
                        scanResponse = lastScanResponse.copy(
                            card = lastScanResponse.card.copy(
                                backupStatus = result.data.backupStatus
                            )
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        success = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        cardNumToBackup = uiState.value.cardNumToBackup + 1
                    )
                }
            }

            is CompletionResult.Failure -> {
                when (val error = result.error) {
                    is TangemSdkError.BackupFailedNotEmptyWallets -> {
                        _errorEvents.trySend(HardwareWalletError.NeedFactoryReset(error.cardId))
                    }
                }
            }
        }
    }

    private fun runWithNetworkCheck(
        action: OfflineAction,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            when {
                connectivityManager.isConnected.value -> {
                    ensureAttestationMode(AttestationTask.Mode.Normal)
                    isOfflineModeForced = false
                    pendingOfflineAction = null
                    block()
                }
                isOfflineModeForced -> {
                    ensureAttestationMode(AttestationTask.Mode.Offline)
                    block()
                }
                else -> {
                    pendingOfflineAction = action
                    _uiState.value = _uiState.value.copy(
                        showOfflineWarningDialog = true
                    )
                }
            }
        }
    }

    private fun ensureAttestationMode(mode: AttestationTask.Mode) {
        if (tangemCreateWalletUseCase.tangemSdkManager.getAttestationMode() != mode) {
            tangemCreateWalletUseCase.tangemSdkManager.setAttestationMode(mode)
        }
    }

    private fun executeOfflineAction(action: OfflineAction) {
        when (action) {
            OfflineAction.CreateWallet -> createWallet()
            OfflineAction.CreateBackup -> createBackup()
            OfflineAction.ProceedBackup -> onWriteFinalDataClicked()
            is OfflineAction.ResetPrimaryCard -> resetCard(action.cardId)
        }
    }
}

internal enum class OnboardingStep(val progress: Float) {
    CREATE_WALLET(0.25f),
    ADD_BACKUP(0.5f),
    CREATE_ACCESS_CODE(0.75f),
    FINAL(1.0f)
}

private sealed interface OfflineAction {
    data object CreateWallet : OfflineAction
    data object CreateBackup : OfflineAction
    data object ProceedBackup : OfflineAction
    data class ResetPrimaryCard(val cardId: String) : OfflineAction
}
