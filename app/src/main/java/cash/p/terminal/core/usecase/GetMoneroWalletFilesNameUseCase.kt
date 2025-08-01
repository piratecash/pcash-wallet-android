package cash.p.terminal.core.usecase

import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetMoneroWalletFilesNameUseCase(
    private val moneroFileDao: MoneroFileDao
) : IGetMoneroWalletFilesNameUseCase {
    override suspend fun invoke(account: Account) = withContext(Dispatchers.IO) {
        when (account.type) {
            is AccountType.MnemonicMonero -> {
                (account.type as AccountType.MnemonicMonero).walletInnerName
            }

            is AccountType.Mnemonic -> {
                moneroFileDao.getAssociatedRecord(account.id)?.fileName?.value
            }

            else -> {
                null
            }
        }
    }
}