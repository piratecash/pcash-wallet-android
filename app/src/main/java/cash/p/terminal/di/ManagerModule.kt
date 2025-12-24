package cash.p.terminal.di

import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.preference.PreferenceManager
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.IBackupManager
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ITorManager
import cash.p.terminal.core.factories.AccountFactory
import cash.p.terminal.core.managers.AdapterManager
import cash.p.terminal.core.managers.BackupManager
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.DefaultCurrencyManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.core.managers.LocalStorageManager
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.PendingTransactionMatcher
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.PendingTransactionRegistrarImpl
import cash.p.terminal.core.managers.PendingTransactionRepository
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.TimePasswordProvider
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.SolanaRpcSourceManager
import cash.p.terminal.core.managers.SolanaWalletManager
import cash.p.terminal.core.managers.StackingManager
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.SystemInfoManager
import cash.p.terminal.core.managers.TokenAutoEnableManager
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TorManager
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.core.managers.DefaultUserManager
import cash.p.terminal.modules.pin.hiddenwallet.HiddenWalletPinPolicy
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.core.converters.PendingTransactionConverter
import cash.p.terminal.modules.addtoken.AddTokenService
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.providers.PendingAccountProvider
import cash.p.terminal.core.providers.PendingAccountProviderImpl
import cash.p.terminal.core.providers.PredefinedBlockchainSettingsProvider
import cash.p.terminal.network.alphaaml.api.AlphaAmlApi
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.modules.transactions.TransactionSyncStateRepository
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.managers.ITransactionHiddenManager
import cash.p.terminal.wallet.managers.UserManager
import com.m2049r.xmrwallet.service.MoneroWalletService
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.IPinSettingsStorage
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.hdwalletkit.Mnemonic
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val managerModule = module {
    singleOf(::SystemInfoManager) bind ISystemInfoManager::class
    singleOf(::BackupManager) bind IBackupManager::class
    singleOf(::LanguageManager)
    singleOf(::DefaultCurrencyManager) bind CurrencyManager::class
    singleOf(::SolanaRpcSourceManager)
    singleOf(::AdapterManager) bind IAdapterManager::class
    singleOf(::LocalStorageManager) {
        bind<ILocalStorage>()
        bind<IPinSettingsStorage>()
    }
    single { PreferenceManager.getDefaultSharedPreferences(get()) }
    singleOf(::BackgroundManager)
    singleOf(::ConnectivityManager) bind IConnectivityManager::class
    singleOf(::EvmSyncSourceManager)
    singleOf(::TokenAutoEnableManager)
    singleOf(::EvmBlockchainManager)
    singleOf(::BtcBlockchainManager)
    singleOf(::BalanceHiddenManager)
    singleOf(::SolanaKitManager)
    singleOf(::StellarKitManager)
    singleOf(::TonKitManager)
    singleOf(::TronKitManager)
    factoryOf(::StackingManager)
    singleOf(::RestoreSettingsManager)
    singleOf(::TimePasswordProvider)
    singleOf(::SeedPhraseQrCrypto)
    singleOf(::EvmLabelManager)
    factoryOf(::SolanaWalletManager)
    singleOf(::RecentAddressManager)
    singleOf(::DefaultUserManager) bind UserManager::class
    singleOf(::AccountFactory) bind IAccountFactory::class
    factoryOf(::WalletActivator)
    factoryOf(::AddTokenService)

    singleOf(::Mnemonic)
    factoryOf(::WordsManager)

    singleOf(::MoneroKitManager)
    singleOf(::MoneroWalletService)
    singleOf(::GlanceAppWidgetManager)

    singleOf(::TransactionAdapterManager)
    singleOf(::TransactionSyncStateRepository)
    singleOf(::BalanceHiddenManager) bind IBalanceHiddenManager::class
    singleOf(::TransactionHiddenManager) bind ITransactionHiddenManager::class
    singleOf(::TorManager) bind ITorManager::class
    singleOf(::PredefinedBlockchainSettingsProvider)
    factory { (pinComponent: IPinComponent) ->
        HiddenWalletPinPolicy(pinComponent, get())
    }

    // Network APIs
    single {
        AlphaAmlApi(
            httpClient = get(),
            baseUrl = AppConfigProvider.alphaAmlBaseUrl,
            apiKey = AppConfigProvider.alphaAmlApiKey
        )
    }

    // Pending transactions
    singleOf(::PendingTransactionRepository)
    singleOf(::PendingTransactionRegistrarImpl) bind PendingTransactionRegistrar::class
    singleOf(::PendingTransactionMatcher)
    singleOf(::PendingAccountProviderImpl) bind PendingAccountProvider::class
    singleOf(::PendingTransactionConverter)
}
