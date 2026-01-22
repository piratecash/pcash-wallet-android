package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.entities.TokenQuery

interface CreateRequiredTokensUseCase {
    suspend operator fun invoke(account: Account, tokenQueries: List<TokenQuery>)
}
