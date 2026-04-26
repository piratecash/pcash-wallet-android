package cash.p.terminal.core.policy

import cash.p.terminal.tangem.domain.policy.TangemHardwareWalletTokenPolicy
import cash.p.terminal.trezor.domain.policy.TrezorHardwareWalletTokenPolicy
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.BlockchainType

class CompositeHardwareWalletTokenPolicy(
    private val tangemPolicy: TangemHardwareWalletTokenPolicy,
    private val trezorPolicy: TrezorHardwareWalletTokenPolicy
) : HardwareWalletTokenPolicy {

    override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType): Boolean = true

    override fun isSupported(account: Account, token: Token): Boolean = when (account.type) {
        is AccountType.HardwareCard -> tangemPolicy.isSupported(token)
        is AccountType.TrezorDevice -> trezorPolicy.isSupported(account, token)
        else -> true
    }
}
