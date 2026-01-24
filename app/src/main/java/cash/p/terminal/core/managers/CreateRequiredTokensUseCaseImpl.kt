package cash.p.terminal.core.managers

import cash.p.terminal.feature.miniapp.domain.usecase.CreateRequiredTokensUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.entities.TokenQuery

class CreateRequiredTokensUseCaseImpl(
    private val walletActivator: WalletActivator,
    private val userDeletedWalletManager: UserDeletedWalletManager
) : CreateRequiredTokensUseCase {
    override suspend fun invoke(account: Account, tokenQueries: List<TokenQuery>) {
        tokenQueries.forEach { tokenQuery ->
            userDeletedWalletManager.unmarkAsDeleted(account.id, tokenQuery.id)
        }
        walletActivator.activateWalletsSuspended(account, tokenQueries)
    }
}
