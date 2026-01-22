package cash.p.terminal.core.managers

import cash.p.terminal.feature.miniapp.domain.usecase.GetTonAddressUseCase
import cash.p.terminal.wallet.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetTonAddressUseCaseImpl(
    private val tonKitManager: TonKitManager
) : GetTonAddressUseCase {
    override suspend fun getAddress(account: Account): String? = withContext(Dispatchers.IO) {
        runCatching {
            val tonKitWrapper = tonKitManager.getNonActiveTonKitWrapper(account, null, null)
            tonKitWrapper.tonKit.receiveAddress.toUserFriendly(false)
        }.getOrNull()
    }
}
