package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet

internal fun Wallet.zcashWatchOnlyUfvk(): String? = when (val type = account.type) {
    is AccountType.ZCashUfvKey -> type.key
    is AccountType.TrezorDevice -> hardwarePublicKey?.key?.value
    else -> null
}
