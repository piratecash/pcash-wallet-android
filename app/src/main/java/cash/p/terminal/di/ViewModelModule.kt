package cash.p.terminal.di

import cash.p.terminal.modules.configuredtoken.ConfiguredTokenInfoViewModel
import cash.p.terminal.modules.createaccount.CreateAdvancedAccountViewModel
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsViewModel
import cash.p.terminal.modules.displayoptions.DisplayOptionsViewModel
import cash.p.terminal.modules.hardwarewallet.HardwareWalletViewModel
import cash.p.terminal.modules.main.MainActivityViewModel
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureViewModel
import cash.p.terminal.modules.premium.about.AboutPremiumViewModel
import cash.p.terminal.modules.premium.settings.PremiumSettingsViewModel
import cash.p.terminal.modules.qrscanner.QRScannerViewModel
import cash.p.terminal.modules.qrscanner.QrCodeImageDecoder
import cash.p.terminal.modules.releasenotes.ReleaseNotesViewModel
import cash.p.terminal.modules.resettofactorysettings.ResetToFactorySettingsViewModel
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicViewModel
import cash.p.terminal.modules.settings.advancedsecurity.AdvancedSecurityViewModel
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SecureResetTermsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsViewModel
import cash.p.terminal.modules.settings.appcache.AppCacheViewModel
import cash.p.terminal.modules.settings.appstatus.AppStatusViewModel
import cash.p.terminal.modules.settings.displaytransactions.DisplayTransactionsViewModel
import cash.p.terminal.modules.settings.privacy.PrivacyViewModel
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsViewModel
import cash.p.terminal.modules.tonconnect.TonConnectListViewModel
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedDialog
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedViewModel
import cash.p.terminal.modules.zcashconfigure.ZcashConfigureViewModel
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.DefaultDispatcherProvider
import io.horizontalsystems.core.DispatcherProvider
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val viewModelModule = module {
    singleOf(::DefaultDispatcherProvider) bind DispatcherProvider::class
    singleOf(::QrCodeImageDecoder)

    viewModelOf(::MainActivityViewModel)
    viewModelOf(::DisplayTransactionsViewModel)
    viewModelOf(::PrivacyViewModel)
    viewModelOf(::HardwareWalletViewModel)
    viewModelOf(::ResetToFactorySettingsViewModel)
    viewModelOf(::SecuritySettingsViewModel)
    viewModelOf(::ReleaseNotesViewModel)
    viewModelOf(::RestoreMnemonicViewModel)
    viewModelOf(::AppStatusViewModel)
    viewModelOf(::AppCacheViewModel)
    viewModelOf(::MoneroConfigureViewModel)
    viewModelOf(::AboutPremiumViewModel)
    viewModelOf(::PremiumSettingsViewModel)
    viewModelOf(::DisplayOptionsViewModel)
    viewModelOf(::TonConnectListViewModel)
    viewModelOf(::ZcashConfigureViewModel)
    viewModelOf(::QRScannerViewModel)
    viewModel { (input: AccountTypeNotSupportedDialog.Input) ->
        AccountTypeNotSupportedViewModel(input = input, accountManager = get())
    }
    viewModel { (token: Token) ->
        ConfiguredTokenInfoViewModel(
            token = token,
            accountManager = get(),
            restoreSettingsManager = get()
        )
    }
    viewModel { (accountToCopy: Account) ->
        DuplicateWalletViewModel(
            accountToCopy = accountToCopy,
            accountManager = get(),
            accountFactory = get(),
            moneroWalletUseCase = get(),
            enabledWalletStorage = get(),
            walletManager = get(),
            restoreSettingsManager = get(),
            localStorage = get()
        )
    }
    viewModelOf(::AdvancedSecurityViewModel)
    viewModelOf(::HiddenWalletTermsViewModel)
    viewModelOf(::SecureResetTermsViewModel)
    viewModelOf(::CreateAdvancedAccountViewModel)
    viewModel { (termTitles: Array<String>) ->
        PassphraseTermsViewModel(termTitles = termTitles, localStorage = get())
    }
}
