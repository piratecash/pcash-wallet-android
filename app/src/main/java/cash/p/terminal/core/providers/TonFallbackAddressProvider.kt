package cash.p.terminal.core.providers

import cash.p.terminal.feature.miniapp.domain.usecase.GetTonAddressUseCase
import cash.p.terminal.wallet.FallbackAddressProvider
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType

class TonFallbackAddressProvider(
    private val getTonAddressUseCase: GetTonAddressUseCase
) : FallbackAddressProvider {

    override suspend fun getAddress(wallet: Wallet): String? {
        if (wallet.token.blockchainType != BlockchainType.Ton) return null
        return getTonAddressUseCase.getAddress(wallet.account)
    }
}
