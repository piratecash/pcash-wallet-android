package cash.p.terminal.di

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.core.factories.AdapterFactory
import cash.p.terminal.core.factories.EvmAccountManagerFactory
import cash.p.terminal.core.managers.AccountCleaner
import cash.p.terminal.core.managers.CoinManager
import cash.p.terminal.core.managers.NumberFormatter
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.providers.EvmLabelProvider
import cash.p.terminal.core.storage.AccountsStorage
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.storage.EnabledWalletsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.core.storage.PendingTransactionStorage
import cash.p.terminal.core.storage.RestoreSettingsStorage
import cash.p.terminal.core.storage.SpamAddressStorage
import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.core.adapters.zcash.ZcashSingleUseAddressManager
import cash.p.terminal.modules.balance.DefaultBalanceService
import cash.p.terminal.modules.balance.DefaultBalanceXRateRepository
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.address.AddressCheckerControl
import cash.p.terminal.modules.send.address.AddressCheckerControlImpl
import cash.p.terminal.modules.transactions.ITransactionRecordRepository
import cash.p.terminal.modules.transactions.TransactionRecordRepository
import cash.p.terminal.modules.transactions.TransactionViewItemFactory
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.wallet.IAccountCleaner
import cash.p.terminal.wallet.IAccountsStorage
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.balance.BalanceService
import cash.p.terminal.wallet.balance.BalanceXRateRepository
import io.horizontalsystems.core.IAppNumberFormatter
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val storageModule = module {
    single { AppDatabase.getInstance(get()) }
    singleOf(::NumberFormatter) bind IAppNumberFormatter::class
    singleOf(::RestoreSettingsStorage) bind IRestoreSettingsStorage::class
    singleOf(::CoinManager) bind ICoinManager::class
    singleOf(::AccountsStorage) bind IAccountsStorage::class
    singleOf(::AccountCleaner) bind IAccountCleaner::class
    singleOf(::EnabledWalletsStorage) bind IEnabledWalletStorage::class
    singleOf(::HardwarePublicKeyStorage) bind IHardwarePublicKeyStorage::class
    singleOf(::BlockchainSettingsStorage)
    singleOf(::SpamAddressStorage)
    factoryOf(::SwapProviderTransactionsStorage)
    factoryOf(::SwapTransactionMatcher)
    singleOf(::EvmSyncSourceStorage)
    singleOf(::ContactsRepository)
    singleOf(::ZcashBirthdayProvider)
    singleOf(::EvmLabelProvider)

    factoryOf(::EvmAccountManagerFactory)
    singleOf(::AdapterFactory)
    factoryOf(::TransactionViewItemFactory)

    factoryOf(::TransactionRecordRepository) bind ITransactionRecordRepository::class
    factoryOf(::AddressCheckerControlImpl) bind AddressCheckerControl::class

    factory<BalanceXRateRepository>(named("wallet")) {
        DefaultBalanceXRateRepository(
            tag = "wallet",
            currencyManager = get(),
            marketKit = get()
        )
    }
    factory<BalanceService>(named("wallet")) {
        DefaultBalanceService.getInstance("wallet")
    }
    factory { get<AppDatabase>().evmAddressLabelDao() }
    factory { get<AppDatabase>().evmMethodLabelDao() }
    factory { get<AppDatabase>().syncerStateDao() }
    factory { get<AppDatabase>().recentAddressDao() }
    factory { get<AppDatabase>().tokenAutoEnabledBlockchainDao() }
    factory { get<AppDatabase>().userDeletedWalletDao() }
    singleOf(::UserDeletedWalletManager)
    factory { get<AppDatabase>().moneroFileDao() }
    factory { get<AppDatabase>().zcashSingleUseAddressDao() }
    single { get<AppDatabase>().spamAddressDao() }
    factory { get<AppDatabase>().pendingTransactionDao() }
    factory { get<AppDatabase>().swapProviderTransactionsDao() }
    singleOf(::PendingTransactionStorage)
    single {
        ZcashSingleUseAddressStorage(
            dao = get(),
            dispatcherProvider = get()
        )
    }
    factory { (accountId: String) ->
        ZcashSingleUseAddressManager(
            storage = get(),
            accountId = accountId
        )
    }
}
