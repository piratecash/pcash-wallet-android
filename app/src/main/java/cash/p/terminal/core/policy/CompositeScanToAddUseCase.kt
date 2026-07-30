package cash.p.terminal.core.policy

import cash.p.terminal.core.usecase.AddMoneroToTrezorAccountUseCase
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.isMonero
import cash.p.terminal.wallet.useCases.ScanToAddUseCase

internal class CompositeScanToAddUseCase(
    private val accountManager: IAccountManager,
    private val tangemScanToAdd: ScanToAddUseCase,
    private val trezorScanToAdd: ScanToAddUseCase,
    private val addMoneroToTrezorAccount: AddMoneroToTrezorAccountUseCase,
) : ScanToAddUseCase {

    override suspend fun addTokensByScan(
        blockchainsToDerive: List<TokenQuery>,
        cardId: String,
        accountId: String
    ): Boolean {
        val account = accountManager.account(accountId)
            ?: error("Account not found")
        return when (val accountType = account.type) {
            is AccountType.TrezorDevice -> {
                check(accountType.deviceId == cardId) {
                    "Trezor device does not match the selected account"
                }
                val publicKeyQueries = blockchainsToDerive.filterNot { it.isMonero }
                val publicKeysCreated = publicKeyQueries.isEmpty() ||
                    trezorScanToAdd.addTokensByScan(publicKeyQueries, cardId, accountId)
                val moneroCreated = if (blockchainsToDerive.any { it.isMonero }) {
                    addMoneroToTrezorAccount(account)
                    true
                } else {
                    true
                }
                publicKeysCreated && moneroCreated
            }

            else -> tangemScanToAdd.addTokensByScan(
                blockchainsToDerive, cardId, accountId
            )
        }
    }

}
