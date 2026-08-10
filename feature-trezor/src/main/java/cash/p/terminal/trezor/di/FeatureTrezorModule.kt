package cash.p.terminal.trezor.di

import android.content.Context
import android.hardware.usb.UsbManager
import cash.p.terminal.trezor.client.TrezorUsbPermissionRequester
import cash.p.terminal.trezor.client.UsbTrezorClientProvider
import cash.p.terminal.trezor.domain.TrezorAccountIdentityValidator
import cash.p.terminal.trezor.domain.policy.TrezorHardwareWalletTokenPolicy
import cash.p.terminal.trezor.domain.usecase.FetchTrezorPublicKeysUseCase
import cash.p.terminal.trezor.domain.usecase.FetchTrezorPublicKeysUseCaseImpl
import cash.p.terminal.trezor.domain.usecase.TrezorScanToAddUseCase
import cash.p.terminal.trezor.ui.TrezorWalletViewModel
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.transport.TrezorUsbCoordinator
import cash.p.terminal.trezorkit.transport.UsbPermissionRequester
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val featureTrezorModule = module {
    singleOf(::TrezorHardwareWalletTokenPolicy)

    single<UsbManager> {
        androidContext().getSystemService(Context.USB_SERVICE) as UsbManager
    }
    singleOf(::TrezorUsbPermissionRequester) bind UsbPermissionRequester::class
    single { TrezorUsbCoordinator(get(), get()) }
    singleOf(::UsbTrezorClientProvider) bind ITrezorClient::class

    singleOf(::TrezorAccountIdentityValidator)
    singleOf(::FetchTrezorPublicKeysUseCaseImpl) bind FetchTrezorPublicKeysUseCase::class
    factory<ScanToAddUseCase>(named("trezor")) { TrezorScanToAddUseCase(get(), get(), get()) }

    viewModel { params -> TrezorWalletViewModel(params.get(), get(), get()) }
}
