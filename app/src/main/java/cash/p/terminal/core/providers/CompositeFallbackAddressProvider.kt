package cash.p.terminal.core.providers

import cash.p.terminal.wallet.FallbackAddressProvider
import cash.p.terminal.wallet.Wallet

/**
 * Combines multiple fallback address providers.
 * Returns first non-null address from any provider.
 */
class CompositeFallbackAddressProvider(
    private val providers: List<FallbackAddressProvider>
) : FallbackAddressProvider {

    override suspend fun getAddress(wallet: Wallet): String? =
        providers.firstNotNullOfOrNull { it.getAddress(wallet) }
}
