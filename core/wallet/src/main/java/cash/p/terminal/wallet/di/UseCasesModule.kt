package cash.p.terminal.wallet.di

import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val useCasesModule = module {
    factoryOf(::RemoveMoneroWalletFilesUseCase)
    singleOf(::WalletFactory)
}