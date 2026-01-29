package cash.p.terminal.core.managers

import cash.p.terminal.core.tryOrNull
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

    override suspend fun getAddress(account: Account): String? = withContext(dispatcherProvider.io) {
        val tokenTypes = listOf(
            TokenType.Native,
            TokenType.Jetton(PIRATE_JETTON_ADDRESS)
        )

        tokenTypes.firstNotNullOfOrNull { tokenType ->
            tryOrNull {
                val wrapper = tonKitManager.getNonActiveTonKitWrapper(account, BlockchainType.Ton, tokenType)
                wrapper.tonKit.receiveAddress.toUserFriendly(false)
            }
        }
    }
}
