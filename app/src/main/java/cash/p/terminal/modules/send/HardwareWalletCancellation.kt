package cash.p.terminal.modules.send

import androidx.annotation.StringRes
import cash.p.terminal.R
import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.trezor.domain.TrezorCancelledException
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException

/** True when [this] is a user-initiated cancellation on any supported hardware wallet (Trezor or Tangem). */
fun Throwable.isHardwareWalletCancelled(): Boolean =
    this is TrezorCancelledException ||
        this is HardwareWalletOperationException && error == HardwareWalletErrorCode.Cancelled ||
        isHardwareWalletUserCancelled()

@StringRes
fun HardwareWalletOperationException.userMessageRes(): Int = when (error) {
    HardwareWalletErrorCode.DeviceNotInitialized -> R.string.trezor_not_initialized_description
    HardwareWalletErrorCode.Network -> R.string.Hud_Text_NoInternet
    else -> R.string.trezor_connect_failed
}

@StringRes
fun Throwable.hardwareWalletUserMessageRes(): Int =
    (this as? HardwareWalletOperationException)?.userMessageRes()
        ?: R.string.trezor_connect_failed
