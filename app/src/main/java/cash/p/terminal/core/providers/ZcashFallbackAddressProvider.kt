package cash.p.terminal.core.providers

import cash.p.terminal.core.adapters.zcash.ZcashAddressDeriver
import cash.p.terminal.core.adapters.zcash.selectZcashReceiver
import cash.p.terminal.core.adapters.zcash.zcashWatchOnlyUfvk
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.FallbackAddressProvider
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

class ZcashFallbackAddressProvider(
    private val deriver: ZcashAddressDeriver,
) : FallbackAddressProvider {

    override suspend fun getAddress(wallet: Wallet): String? {
        if (wallet.token.blockchainType != BlockchainType.Zcash) return null
        return tryOrNull { deriveAddress(wallet) }
    }

    private suspend fun deriveAddress(wallet: Wallet): String? {
        val spec = when (val tokenType = wallet.token.type) {
            is TokenType.AddressSpecTyped -> tokenType.type
            TokenType.Native -> null
            else -> return null
        }
        val unified = unifiedAddress(wallet) ?: return null
        return spec.selectZcashReceiver(
            sapling = { deriver.saplingReceiver(unified) },
            transparent = { deriver.transparentReceiver(unified) },
            unified = { unified },
        )
    }

    private suspend fun unifiedAddress(wallet: Wallet): String? {
        wallet.zcashWatchOnlyUfvk()?.let { return deriver.deriveUnifiedAddressFromUfvk(it) }
        val seed = (wallet.account.type as? AccountType.Mnemonic)?.seed ?: return null
        return deriver.deriveUnifiedAddressFromSeed(seed)
    }
}
