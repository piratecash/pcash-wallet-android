package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

interface IHardwarePublicKeyStorage {
    fun deleteAll()
    suspend fun save(keys: List<HardwarePublicKey>)
    suspend fun getKey(accountId: String, blockchainType: BlockchainType, tokenType: TokenType): HardwarePublicKey?

    /**
     * Returns an arbitrary matching row for [accountId] and [blockchainType]
     * (`WHERE accountId AND blockchainType LIMIT 1`, no `tokenType` filter, no `ORDER BY`).
     *
     * Safe ONLY when every [HardwarePublicKey] for that (accountId, blockchainType) pair
     * intentionally shares the same key and derivationPath (e.g. EVM chains, where the native
     * coin and all its tokens use one account key) AND the caller does not need a
     * token-specific derivation. Multiple rows per chain are normal (native + tokens); the
     * invariant required here is that they are IDENTICAL keys/paths, not that there is only one.
     *
     * For BTC-family networks (Bitcoin / Litecoin / BitcoinCash) where each derivation/address
     * type has its own key and path, use [getKey] with the specific `tokenType` instead.
     */
    suspend fun getKeyByBlockchain(accountId: String, blockchainType: BlockchainType): HardwarePublicKey?
    suspend fun getAllPublicKeys(accountId: String): List<HardwarePublicKey>
}