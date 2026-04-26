package cash.p.terminal.trezor.domain.usecase

import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

internal class TrezorScanToAddUseCase(
    private val fetchTrezorPublicKeysUseCase: FetchTrezorPublicKeysUseCase,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val dispatcherProvider: DispatcherProvider
) : ScanToAddUseCase {

    override suspend fun addTokensByScan(
        blockchainsToDerive: List<TokenQuery>,
        cardId: String,
        accountId: String
    ): Boolean {
        val publicKeys = fetchTrezorPublicKeysUseCase(blockchainsToDerive, accountId)
        withContext(dispatcherProvider.io) {
            hardwarePublicKeyStorage.save(publicKeys)
        }
        return publicKeys.size == blockchainsToDerive.size
    }
}
