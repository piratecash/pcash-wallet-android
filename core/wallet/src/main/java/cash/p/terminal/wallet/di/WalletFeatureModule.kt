package cash.p.terminal.wallet.di

import cash.p.terminal.wallet.AccountManager
import cash.p.terminal.wallet.HSCache
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.IWalletStorage
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.SubscriptionManager
import cash.p.terminal.wallet.WalletManager
import cash.p.terminal.wallet.WalletStorage
import cash.p.terminal.wallet.providers.CryptoCompareProvider
import cash.p.terminal.wallet.providers.RetrofitUtils
import cash.p.terminal.wallet.storage.MarketDatabase
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import cash.p.terminal.wallet.useCases.WalletUseCase
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val walletFeatureModule = module {
    single(named("retrofitOkHttpClient")) {
        val cache = HSCache.cacheDir?.let {
            Cache(it, HSCache.cacheQuotaBytes)
        }
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            setLevel(HttpLoggingInterceptor.Level.BASIC)
        }

        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .cache(cache)
            .build()
    }

    single { RetrofitUtils(get(named("retrofitOkHttpClient"))) }

    singleOf(::WalletManager) bind IWalletManager::class
    singleOf(::AccountManager) bind IAccountManager::class
    singleOf(::WalletStorage) bind IWalletStorage::class
    singleOf(::MarketKitWrapper)
    singleOf(::SubscriptionManager)
    factoryOf(::WalletUseCase)
    factoryOf(::CryptoCompareProvider)
    single { MarketDatabase.getInstance(get()) }

    singleOf(::GetHardwarePublicKeyForWalletUseCase)
    singleOf(::PassphraseValidator)

    includes(mappersModule, useCasesModule)
}