package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.wallet.Account

interface GetPirateJettonAddressUseCase {
    suspend fun getAddress(account: Account): String?
}
