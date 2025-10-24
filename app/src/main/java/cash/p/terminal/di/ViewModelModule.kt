package cash.p.terminal.di

import cash.p.terminal.core.DefaultDispatcherProvider
import cash.p.terminal.core.DispatcherProvider
import cash.p.terminal.modules.hardwarewallet.HardwareWalletViewModel
import cash.p.terminal.modules.main.MainActivityViewModel
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureViewModel
import cash.p.terminal.modules.premium.about.AboutPremiumViewModel
import cash.p.terminal.modules.premium.settings.PremiumSettingsViewModel
import cash.p.terminal.modules.releasenotes.ReleaseNotesViewModel
import cash.p.terminal.modules.resettofactorysettings.ResetToFactorySettingsViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicViewModel
import cash.p.terminal.modules.settings.appstatus.AppStatusViewModel
import cash.p.terminal.modules.settings.displaytransactions.DisplayTransactionsViewModel
import cash.p.terminal.modules.settings.privacy.PrivacyViewModel
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedDialog
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedViewModel
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsViewModel
import cash.p.terminal.modules.displayoptions.DisplayOptionsViewModel
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletViewModel
import cash.p.terminal.modules.settings.advancedsecurity.AdvancedSecurityViewModel
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SecureResetTermsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsViewModel
import cash.p.terminal.modules.tonconnect.TonConnectListViewModel
import cash.p.terminal.modules.zcashconfigure.ZcashConfigureViewModel
import cash.p.terminal.wallet.Account
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    singleOf(::DefaultDispatcherProvider) bind DispatcherProvider::class

    viewModelOf(::MainActivityViewModel)
    viewModelOf(::DisplayTransactionsViewModel)
    viewModelOf(::PrivacyViewModel)
    viewModelOf(::HardwareWalletViewModel)
    viewModelOf(::ResetToFactorySettingsViewModel)
    viewModelOf(::SecuritySettingsViewModel)
    viewModelOf(::ReleaseNotesViewModel)
    viewModelOf(::RestoreMnemonicViewModel)
    viewModelOf(::AppStatusViewModel)
    viewModelOf(::MoneroConfigureViewModel)
    viewModelOf(::AboutPremiumViewModel)
    viewModelOf(::PremiumSettingsViewModel)
    viewModelOf(::DisplayOptionsViewModel)
    viewModelOf(::TonConnectListViewModel)
    viewModelOf(::ZcashConfigureViewModel)
    viewModel { (input: AccountTypeNotSupportedDialog.Input) ->
        AccountTypeNotSupportedViewModel(input = input, accountManager = get())
    }
    viewModel { (accountToCopy: Account) ->
        DuplicateWalletViewModel(
            accountToCopy = accountToCopy,
            accountManager = get(),
            accountFactory = get(),
            moneroWalletUseCase = get(),
            enabledWalletStorage = get(),
            walletManager = get(),
            restoreSettingsManager = get()
        )
    }
    viewModelOf(::AdvancedSecurityViewModel)
    viewModelOf(::HiddenWalletTermsViewModel)
    viewModelOf(::SecureResetTermsViewModel)
}
