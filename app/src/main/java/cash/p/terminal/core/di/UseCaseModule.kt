package cash.p.terminal.core.di

import cash.p.terminal.core.usecase.CreateHardwareWalletUseCase
import cash.p.terminal.core.usecase.UpdateChangeNowStatusesUseCase
import cash.p.terminal.tangem.domain.usecase.ICreateHardwareWalletUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val useCaseModule = module {
    factoryOf(::UpdateChangeNowStatusesUseCase)
    factoryOf(::CreateHardwareWalletUseCase) bind ICreateHardwareWalletUseCase::class
}