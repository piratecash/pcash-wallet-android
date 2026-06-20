package cash.p.terminal.modules.send.offline

import cash.p.terminal.core.defaultTokenQuery
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.nativeTokenQueries
import cash.p.terminal.core.supports
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.BlockchainType

/**
 * Resolves the token whose wallet must be enabled before an offline-signed RAW transaction can be
 * relayed on a given blockchain. Capability is derived from existing layers instead of a
 * hand-maintained matrix, so enabling offline broadcast for a new network requires no change here:
 *
 *  - [BtcBlockchainManager.blockchainTypes] is exactly the set the adapter factory builds a
 *    BitcoinBaseAdapter for, and BitcoinBaseAdapter is the only family that implements
 *    OfflineBroadcastAdapter. A new bitcoin-like adapter added there starts relaying automatically.
 *  - Watch-only accounts (watch address and public HD extended key) are rejected because the
 *    bitcoin-kit core is read-only and throws CoreError.ReadOnlyCore on broadcast.
 *  - Token/account compatibility reuses [Token.supports] + [BlockchainType.supports], which already
 *    encode derivation, purpose and coin-type constraints (e.g. native Dogecoin is not relayable
 *    from an HD extended key).
 *  - Hardware-wallet limits reuse [HardwareWalletTokenPolicy] (Tangem exclusions and Trezor's
 *    per-model network support), so they stay correct as those policies evolve.
 *
 * The post-enable adapter lookup remains authoritative, so an over-permissive answer here surfaces a
 * graceful error rather than a crash.
 */
class OfflineBroadcastTokenResolver(
    private val marketKit: MarketKitWrapper,
    private val btcBlockchainManager: BtcBlockchainManager,
    private val hardwareWalletTokenPolicy: HardwareWalletTokenPolicy,
) {

    fun resolveTokenToEnable(blockchainType: BlockchainType, account: Account): Token? {
        if (account.isWatchAccount) return null
        if (blockchainType !in btcBlockchainManager.blockchainTypes) return null

        // Derivation is irrelevant for relaying signed bytes; any compatible token can host the
        // local wallet/adapter. Prefer the default query, then fall back to other native queries.
        val queries = (listOf(blockchainType.defaultTokenQuery) + blockchainType.nativeTokenQueries)
            .distinct()
        return queries.firstNotNullOfOrNull { query ->
            marketKit.token(query)?.takeIf { canEnable(it, account) }
        }
    }

    private fun canEnable(token: Token, account: Account): Boolean =
        token.supports(account.type) &&
            token.blockchainType.supports(account.type) &&
            hardwareWalletTokenPolicy.isSupported(account, token)
}
