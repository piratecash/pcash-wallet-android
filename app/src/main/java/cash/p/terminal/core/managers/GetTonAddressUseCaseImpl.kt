package cash.p.terminal.core.managers

import cash.p.terminal.feature.miniapp.domain.usecase.GetTonAddressUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.BuildConfig.PIRATE_JETTON_ADDRESS
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.withContext

class GetTonAddressUseCaseImpl(
    private val tonKitManager: TonKitManager,
    private val dispatcherProvider: DispatcherProvider
) : GetTonAddressUseCase {

    override suspend fun getAddress(account: Account): String =
        withContext(dispatcherProvider.io) {
            val tokenTypes = listOf(
                TokenType.Native,
                TokenType.Jetton(PIRATE_JETTON_ADDRESS)
            )

            var lastException: Throwable? = null
            for (tokenType in tokenTypes) {
                try {
                    return@withContext tonKitManager.getTonWallet(
                        account,
                        BlockchainType.Ton,
                        tokenType
                    ).address.toUserFriendly(false)
                } catch (e: Throwable) {
                    lastException = e
                }
            }
            throw lastException ?: error("Can't get TON/JETTON address for account")
        }
}
