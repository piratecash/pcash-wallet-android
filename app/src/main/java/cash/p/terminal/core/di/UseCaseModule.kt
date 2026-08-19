package cash.p.terminal.core.di

import cash.p.terminal.core.policy.CompositeHardwareWalletTokenPolicy
import cash.p.terminal.core.policy.CompositeScanToAddUseCase
import cash.p.terminal.core.usecase.AddMoneroToTrezorAccountUseCase
import cash.p.terminal.core.usecase.CreateHardwareWalletUseCase
import cash.p.terminal.core.usecase.CreateTrezorWalletUseCase
import cash.p.terminal.core.usecase.GenerateMoneroWalletUseCase
import cash.p.terminal.core.usecase.GetMoneroWalletFilesNameUseCase
import cash.p.terminal.core.usecase.GetRestoreHeightForWalletUseCase
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.core.usecase.FetchSwapQuotesUseCase
import cash.p.terminal.core.usecase.IterativeExactOutSearch
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.core.usecase.RescanMoneroUseCase
import cash.p.terminal.core.usecase.RescanZcashUseCase
import cash.p.terminal.core.usecase.ResolvePayCoreNavigationUseCase
import cash.p.terminal.core.usecase.ResolveTransactionItemUseCase
import cash.p.terminal.core.usecase.SyncPendingMultiSwapUseCase
import cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.core.usecase.ValidateMoneroMnemonicUseCase
import cash.p.terminal.domain.usecase.ClearZCashWalletDataUseCase
import cash.p.terminal.domain.usecase.GetLocalizedAssetUseCase
import cash.p.terminal.domain.usecase.DeleteAllContactsUseCase
import cash.p.terminal.domain.usecase.ResetUseCase
import cash.p.terminal.manager.ITorConnectionStatusUseCase
import cash.p.terminal.modules.pin.SendZecOnDuressUseCase
import cash.p.terminal.modules.tor.TorConnectionStatusUseCase
import cash.p.terminal.tangem.domain.usecase.ICreateHardwareWalletUseCase
import cash.p.terminal.trezor.domain.usecase.ICreateTrezorWalletUseCase
import cash.p.terminal.trezor.domain.usecase.TrezorMoneroRestoreHeightResolver
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val useCaseModule = module {
    singleOf(::UpdateSwapProviderTransactionsStatusUseCase)
    singleOf(::SyncPendingMultiSwapUseCase)
    factoryOf(::FetchSwapQuotesUseCase)
    factoryOf(::IterativeExactOutSearch)
    factoryOf(::ResolveTransactionItemUseCase)
    factoryOf(::ResolvePayCoreNavigationUseCase)
    factoryOf(::ValidateMoneroMnemonicUseCase)
    factoryOf(::ValidateMoneroHeightUseCase) bind TrezorMoneroRestoreHeightResolver::class
    singleOf(::AddMoneroToTrezorAccountUseCase)
    factoryOf(::GetLocalizedAssetUseCase)
    factoryOf(::MoneroWalletUseCase)
    factoryOf(::GenerateMoneroWalletUseCase)
    factoryOf(::GetRestoreHeightForWalletUseCase)
    factoryOf(::RescanMoneroUseCase)
    factoryOf(::RescanZcashUseCase)
    factoryOf(::CreateHardwareWalletUseCase) bind ICreateHardwareWalletUseCase::class
    factoryOf(::CreateTrezorWalletUseCase) bind ICreateTrezorWalletUseCase::class
    factoryOf(::GetMoneroWalletFilesNameUseCase) bind IGetMoneroWalletFilesNameUseCase::class
    singleOf(::TorConnectionStatusUseCase) bind ITorConnectionStatusUseCase::class
    singleOf(::ClearZCashWalletDataUseCase)
    singleOf(::OfflineModeUseCase)
    singleOf(::DeleteAllContactsUseCase)
    singleOf(::ResetUseCase)
    singleOf(::SendZecOnDuressUseCase)
    singleOf(::CompositeHardwareWalletTokenPolicy) bind HardwareWalletTokenPolicy::class
    single<ScanToAddUseCase> {
        CompositeScanToAddUseCase(
            get(),
            get(named("tangem")),
            get(named("trezor")),
            get(),
        )
    }
}
