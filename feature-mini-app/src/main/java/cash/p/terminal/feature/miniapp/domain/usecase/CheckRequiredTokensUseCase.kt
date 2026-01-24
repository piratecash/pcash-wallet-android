package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CheckRequiredTokensUseCase(
    private val walletManager: IWalletManager,
    private val marketKitWrapper: MarketKitWrapper
) {
    data class Result(
        val allTokens: List<Token>,
        val missingTokens: List<Token>,
        val missingTokenQueries: List<TokenQuery>
    ) {
        val allTokensExist: Boolean get() = missingTokens.isEmpty()
    }

    suspend operator fun invoke(account: Account): Result = withContext(Dispatchers.IO) {
        val requiredQueries = listOf(
            TokenQuery.PirateJetton,
            TokenQuery.PirateCashBnb,
            TokenQuery.CosantaBnb
        )

        val activeWalletTokens = walletManager.getWallets(account).map { it.token }.toSet()
        val tokensByQuery = requiredQueries.associateWith { marketKitWrapper.token(it) }

        val allTokens = tokensByQuery.values.filterNotNull()
        val missingPairs = tokensByQuery.filter { (_, token) ->
            token != null && token !in activeWalletTokens
        }

        Result(
            allTokens = allTokens,
            missingTokens = missingPairs.values.filterNotNull(),
            missingTokenQueries = missingPairs.keys.toList()
        )
    }
}
