package cash.p.terminal.core.managers

import cash.p.terminal.feature.miniapp.domain.usecase.GetPirateJettonAddressUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.entities.TokenQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetPirateJettonAddressUseCaseImpl(
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager
) : GetPirateJettonAddressUseCase {
    override suspend fun getAddress(account: Account): String? = withContext(Dispatchers.IO) {
        val pirateJettonWallet = walletManager.activeWallets.find { wallet ->
            wallet.account == account && wallet.token.tokenQuery == TokenQuery.PirateJetton
        } ?: return@withContext null

        adapterManager.getReceiveAdapterForWallet(pirateJettonWallet)?.receiveAddress
    }
}
