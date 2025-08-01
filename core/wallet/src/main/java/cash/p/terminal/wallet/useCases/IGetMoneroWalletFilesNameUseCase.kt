package cash.p.terminal.wallet.useCases

import cash.p.terminal.wallet.Account

interface IGetMoneroWalletFilesNameUseCase {
    suspend operator fun invoke(account: Account): String?
}