package cash.p.terminal.modules.send

import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.trezor.domain.TrezorCancelledException

/** True when [this] is a user-initiated cancellation on any supported hardware wallet (Trezor or Tangem). */
fun Throwable.isHardwareWalletCancelled(): Boolean =
    this is TrezorCancelledException || isHardwareWalletUserCancelled()
