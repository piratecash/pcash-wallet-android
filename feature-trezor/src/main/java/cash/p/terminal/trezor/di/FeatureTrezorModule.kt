package cash.p.terminal.trezor.di

import cash.p.terminal.trezor.domain.TrezorDeepLinkManager
import cash.p.terminal.trezor.domain.TrezorSuiteInstallChecker
import cash.p.terminal.trezor.domain.policy.TrezorHardwareWalletTokenPolicy
import cash.p.terminal.trezor.domain.usecase.FetchTrezorPublicKeysUseCase
import cash.p.terminal.trezor.domain.usecase.FetchTrezorPublicKeysUseCaseImpl
import cash.p.terminal.trezor.domain.usecase.TrezorScanToAddUseCase
import cash.p.terminal.trezor.ui.TrezorWalletViewModel
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val featureTrezorModule = module {
    singleOf(::TrezorDeepLinkManager)
    singleOf(::TrezorSuiteInstallChecker)
    singleOf(::TrezorHardwareWalletTokenPolicy)
    singleOf(::FetchTrezorPublicKeysUseCaseImpl) bind FetchTrezorPublicKeysUseCase::class
    factory<ScanToAddUseCase>(named("trezor")) { TrezorScanToAddUseCase(get(), get(), get()) }

    viewModel { params -> TrezorWalletViewModel(params.get(), get(), get()) }
}
