package cash.p.terminal.core.managers

import cash.p.terminal.feature.miniapp.domain.usecase.CreateRequiredTokensUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.useCases.ScanToAddUseCase

class CreateRequiredTokensUseCaseImpl(
    private val walletActivator: WalletActivator,
    private val userDeletedWalletManager: UserDeletedWalletManager,
    private val scanToAddUseCase: ScanToAddUseCase,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage
) : CreateRequiredTokensUseCase {
    override suspend fun invoke(account: Account, tokenQueries: List<TokenQuery>) {
        tokenQueries.forEach { tokenQuery ->
            userDeletedWalletManager.unmarkAsDeleted(account.id, tokenQuery.id)
        }

        val hardwareCard = account.type as? AccountType.HardwareCard
        if (hardwareCard != null) {
            val existingKeys = hardwarePublicKeyStorage.getAllPublicKeys(account.id)
            val missingQueries = tokenQueries.filter { query ->
                existingKeys.none {
                    it.blockchainType == query.blockchainType.uid &&
                    it.tokenType == query.tokenType
                }
            }

            if (missingQueries.isNotEmpty()) {
                scanToAddUseCase.addTokensByScan(
                    blockchainsToDerive = missingQueries,
                    cardId = hardwareCard.cardId,
                    accountId = account.id
                )
            }
        }

        walletActivator.activateWalletsSuspended(account, tokenQueries)
    }
}
