package cash.p.terminal.trezor.domain.policy

import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezor.domain.TrezorMoneroAdmissionPolicy
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.BlockchainType

class TrezorHardwareWalletTokenPolicy : HardwareWalletTokenPolicy {
    override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType): Boolean {
        return TrezorPublicKeySpecs.supports(null, blockchainType, tokenType)
    }

    override fun isSupported(account: Account, token: Token): Boolean {
        val accountType = account.type as? AccountType.TrezorDevice ?: return false
        if (
            TrezorMoneroAdmissionPolicy.supportsStoredToken(
                accountType.model,
                token.blockchainType,
                token.type,
            )
        ) {
            return true
        }
        val model = TrezorModel.fromInternalModel(accountType.model)
        return TrezorPublicKeySpecs.supports(model, token.blockchainType, token.type)
    }
}
