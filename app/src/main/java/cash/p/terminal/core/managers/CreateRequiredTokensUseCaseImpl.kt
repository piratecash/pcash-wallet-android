package cash.p.terminal.core.managers

import cash.p.terminal.feature.miniapp.domain.usecase.CreateRequiredTokensUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.entities.TokenQuery

class CreateRequiredTokensUseCaseImpl(
    private val walletActivator: WalletActivator
) : CreateRequiredTokensUseCase {
    override suspend fun invoke(account: Account, tokenQueries: List<TokenQuery>) {
        walletActivator.activateWalletsSuspended(account, tokenQueries)
    }
}
