package cash.p.terminal.trezor.domain.usecase

import cash.p.terminal.wallet.AccountType

interface ICreateTrezorWalletUseCase {
    suspend operator fun invoke(accountName: String): AccountType.TrezorDevice
}
