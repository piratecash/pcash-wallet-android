package cash.p.terminal.modules.coin

import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

object CoinConfig {
    /**
     * Tokens for which the Analytics/Details tab should be hidden.
     * Add new TokenQuery entries here to disable analytics for additional tokens.
     */
    val analyticsDisabledTokens: Set<TokenQuery> = setOf(
        TokenQuery(BlockchainType.Cosanta, TokenType.Native),  // Cosa Native
        TokenQuery.CosantaBnb                                   // Cosa BEP20
    )
}

/**
 * Returns true if analytics should be shown for this coin.
 */
fun FullCoin.isAnalyticsEnabled(): Boolean {
    return tokens.none { token ->
        CoinConfig.analyticsDisabledTokens.contains(token.tokenQuery)
    }
}
