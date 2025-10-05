package cash.p.terminal.tangem.domain.policy

import cash.p.terminal.tangem.domain.TangemConfig
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.BlockchainType

class TangemHardwareWalletTokenPolicy : HardwareWalletTokenPolicy {
    override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType): Boolean {
        return !TangemConfig.isExcludedForHardwareCard(blockchainType, tokenType)
    }
}
