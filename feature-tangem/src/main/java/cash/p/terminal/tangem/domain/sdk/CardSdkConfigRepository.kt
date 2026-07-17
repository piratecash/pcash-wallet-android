package cash.p.terminal.tangem.domain.sdk

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import com.tangem.Log
import com.tangem.TangemSdk
import com.tangem.common.UserCodeType
import com.tangem.common.core.UserCodeRequestPolicy
import com.tangem.operations.attestation.AttestationTask
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.launch

class CardSdkConfigRepository(
    private val cardSdkProvider: CardSdkProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val accountManager: IAccountManager
) {

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

    /**
     * Re-enables NFC reader mode when the OS silently disabled it outside the Activity lifecycle.
     * Targeted workaround for Samsung devices: opening the camera / QR scanner there turns reader
     * mode off without any lifecycle callback, leaving a Tangem card unreadable on the next sign
     * (the scanning sheet shows but the card is never read).
     *
     * Skipped unless the user has a hardware card wallet — software wallets never use NFC, so there
     * is nothing to restore. Fire-and-forget on the application scope (IO) because the underlying SDK
     * call blocks (~500 ms) and must survive the caller screen being destroyed.
     */
    fun forceEnableReaderMode() {
        if (accountManager.accounts.none { it.type is AccountType.HardwareCard }) return
        dispatcherProvider.applicationScope.launch {
            try {
                sdk.forceEnableReaderMode()
            } catch (e: Throwable) {
                Log.error { "forceEnableReaderMode failed: ${e.message}" }
            }
        }
    }
}
