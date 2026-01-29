package cash.p.terminal.wallet

/**
 * Provides receive address when adapter is not synced.
 * Each implementation supports specific blockchain types.
 */
interface FallbackAddressProvider {
    /**
     * Get receive address for wallet without adapter sync.
     * Returns null if blockchain not supported or address unavailable.
     */
    suspend fun getAddress(wallet: Wallet): String?
}
