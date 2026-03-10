package cash.p.terminal.core.policy

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.useCases.ScanToAddUseCase

class CompositeScanToAddUseCase(
    private val accountManager: IAccountManager,
    private val tangemScanToAdd: ScanToAddUseCase,
    private val trezorScanToAdd: ScanToAddUseCase
) : ScanToAddUseCase {

    override suspend fun addTokensByScan(
        blockchainsToDerive: List<TokenQuery>,
        cardId: String,
        accountId: String
    ): Boolean {
        val account = accountManager.activeAccount
            ?: error("No active account")
        return when (account.type) {
            is AccountType.TrezorDevice -> trezorScanToAdd.addTokensByScan(
                blockchainsToDerive, cardId, accountId
            )
            else -> tangemScanToAdd.addTokensByScan(
                blockchainsToDerive, cardId, accountId
            )
        }
    }
}
