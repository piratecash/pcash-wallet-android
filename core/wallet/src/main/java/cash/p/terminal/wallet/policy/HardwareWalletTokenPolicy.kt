package cash.p.terminal.wallet.policy

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

interface HardwareWalletTokenPolicy {
    fun isSupported(blockchainType: BlockchainType, tokenType: TokenType): Boolean

    fun isSupported(token: Token): Boolean {
        return isSupported(token.blockchainType, token.type)
    }

    fun isSupported(account: Account, token: Token): Boolean {
        return isSupported(token)
    }
}
