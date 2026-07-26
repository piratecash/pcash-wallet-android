package cash.p.terminal.tangem.domain.sdk

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import com.tangem.TangemSdk
import com.tangem.common.UserCodeType
import com.tangem.common.core.UserCodeRequestPolicy
import com.tangem.operations.attestation.AttestationTask
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class CardSdkConfigRepository(
    private val cardSdkProvider: CardSdkProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val accountManager: IAccountManager
) {

    private var readerModeRestoreJob: Job? = null

    val sdk: TangemSdk
        get() = cardSdkProvider.sdk

    var isBiometricsRequestPolicy: Boolean
        get() = sdk.config.userCodeRequestPolicy is UserCodeRequestPolicy.AlwaysWithBiometrics
        set(value) {
            sdk.config.userCodeRequestPolicy = if (value) {
                UserCodeRequestPolicy.AlwaysWithBiometrics(codeType = UserCodeType.AccessCode)
            } else {
                UserCodeRequestPolicy.Default
            }
        }

    fun getAttestationMode(): AttestationTask.Mode = sdk.config.attestationMode

    fun setAttestationMode(mode: AttestationTask.Mode) {
        sdk.config.attestationMode = mode
    }

    fun cancelSession() {
        cardSdkProvider.cancelSession()
    }

    fun disableReaderModeForQrScanner() {
        cancelReaderModeRestore()
        runIfHardwareCardPresent {
            updateReaderMode("forceDisableReaderMode", TangemSdk::forceDisableReaderMode)
        }
    }

    fun restoreReaderModeAfterQrScanner() {
        cancelReaderModeRestore()
        runIfHardwareCardPresent {
            readerModeRestoreJob = dispatcherProvider.applicationScope.launch {
                // Some devices cannot enable reader mode until CameraX teardown and navigation finish.
                delay(READER_MODE_RESTORE_DELAY)
                updateReaderMode("forceEnableReaderMode", TangemSdk::forceEnableReaderMode)
            }
        }
    }

    private fun cancelReaderModeRestore() {
        readerModeRestoreJob?.cancel()
        readerModeRestoreJob = null
    }

    private inline fun runIfHardwareCardPresent(action: () -> Unit) {
        if (accountManager.accounts.any { it.type is AccountType.HardwareCard }) action()
    }

    private fun updateReaderMode(operation: String, action: TangemSdk.() -> Unit) {
        try {
            sdk.action()
        } catch (e: Throwable) {
            Timber.e(e, "$operation failed")
        }
    }

    private companion object {
        const val READER_MODE_RESTORE_DELAY = 1_000L
    }
}
