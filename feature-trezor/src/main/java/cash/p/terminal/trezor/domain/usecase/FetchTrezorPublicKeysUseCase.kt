package cash.p.terminal.trezor.domain.usecase

import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery

interface FetchTrezorPublicKeysUseCase {
    suspend operator fun invoke(
        tokenQueries: List<TokenQuery>,
        accountId: String
    ): List<HardwarePublicKey>
}
