package cash.p.terminal.network.zcash.di

import cash.p.terminal.network.zcash.api.ZcashApi
import cash.p.terminal.network.zcash.data.GetZcashHeightUseCaseImpl
import cash.p.terminal.network.zcash.data.Logger
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val networkZcashModule = module {
    factoryOf(::ZcashApi)
    singleOf(::Logger)
    factoryOf(::GetZcashHeightUseCaseImpl) bind GetZcashHeightUseCase::class
}
