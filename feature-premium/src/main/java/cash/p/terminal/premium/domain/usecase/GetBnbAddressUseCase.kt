package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.wallet.Account

interface GetBnbAddressUseCase {
    suspend fun getAddress(account: Account, requestScanTangemIfNotFound: Boolean = false): String?
    suspend fun deleteBnbAddress(accountId: String)
    suspend fun saveAddress(address: String, accountId: String)
    suspend fun deleteExcludeAccountIds(accountIds: List<String>)
}