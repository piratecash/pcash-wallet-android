package cash.p.terminal.trezor.domain.policy

import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.BlockchainType

class TrezorHardwareWalletTokenPolicy : HardwareWalletTokenPolicy {
    override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType): Boolean {
        return TrezorModelSupport.isSupported(null, blockchainType)
    }

    override fun isSupported(account: Account, token: Token): Boolean {
        val model = (account.type as? AccountType.TrezorDevice)?.let {
            TrezorModel.fromInternalModel(it.model)
        }
        return TrezorModelSupport.isSupported(model, token.blockchainType)
    }
}
