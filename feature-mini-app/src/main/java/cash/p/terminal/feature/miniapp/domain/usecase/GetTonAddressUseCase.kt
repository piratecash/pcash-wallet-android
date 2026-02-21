package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.wallet.Account

interface GetTonAddressUseCase {
    suspend fun getAddress(account: Account): String
}
