package cash.p.terminal.feature.miniapp.di

import cash.p.terminal.feature.miniapp.data.api.MiniAppApi
import cash.p.terminal.feature.miniapp.data.detector.EmulatorDetector
import cash.p.terminal.feature.miniapp.data.repository.CaptchaRepository
import cash.p.terminal.feature.miniapp.domain.usecase.CaptchaUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.CheckIfEmulatorUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.CheckRequiredTokensUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.CollectDeviceEnvironmentUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.GetSpecialProposalDataUseCase
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppViewModel
import cash.p.terminal.feature.miniapp.ui.miniapp.MiniAppViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val featureMiniAppModule = module {
    // Detector
    singleOf(::EmulatorDetector)

    // API
    singleOf(::MiniAppApi)

    // Repository
    singleOf(::CaptchaRepository)

    // Use cases
    singleOf(::CheckIfEmulatorUseCase)
    singleOf(::CollectDeviceEnvironmentUseCase)
    singleOf(::CaptchaUseCase)
    singleOf(::GetSpecialProposalDataUseCase)
    singleOf(::CheckRequiredTokensUseCase)

    // ViewModels
    viewModelOf(::MiniAppViewModel)
    viewModel {
        ConnectMiniAppViewModel(
            checkIfEmulatorUseCase = get(),
            collectDeviceEnvironmentUseCase = get(),
            checkPremiumUseCase = get(),
            captchaUseCase = get(),
            getSpecialProposalDataUseCase = get(),
            checkRequiredTokensUseCase = get(),
            createRequiredTokensUseCase = get(),
            getPirateJettonAddressUseCase = get(),
            accountManager = get(),
            marketKitWrapper = get(),
            balanceService = get(named("wallet")),
            getBnbAddressUseCase = get(),
            uniqueCodeStorage = get(),
            savedStateHandle = get()
        )
    }
}
