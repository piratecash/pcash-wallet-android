package cash.p.terminal.premium.di

import cash.p.terminal.premium.data.api.EthereumRpcApi
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.premium.data.repository.PremiumUserRepository
import cash.p.terminal.premium.domain.repository.TokenBalanceRepository
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCaseImpl
import cash.p.terminal.premium.domain.usecase.SeedToEvmAddressUseCase
import cash.p.terminal.premium.domain.usecase.CheckTrialPremiumUseCase
import cash.p.terminal.premium.domain.usecase.ActivateTrialPremiumUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val featurePremiumModule = module {
    // Database
    single { PremiumDatabase.create(get()) }
    single { get<PremiumDatabase>().premiumUserDao() }
    single { get<PremiumDatabase>().demoPremiumUserDao() }

    // API
    factoryOf(::EthereumRpcApi)

    // Repositories
    factoryOf(::TokenBalanceRepository)
    factoryOf(::PremiumUserRepository)

    // Use Cases
    singleOf(::CheckPremiumUseCaseImpl) bind CheckPremiumUseCase::class
    factoryOf(::SeedToEvmAddressUseCase)
    factoryOf(::CheckTrialPremiumUseCase)
    factoryOf(::ActivateTrialPremiumUseCase) bind ActivateTrialPremiumUseCase::class
}