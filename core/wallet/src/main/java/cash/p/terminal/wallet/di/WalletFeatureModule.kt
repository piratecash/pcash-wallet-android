package cash.p.terminal.wallet.di

import cash.p.terminal.wallet.AccountManager
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.IWalletStorage
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.SubscriptionManager
import cash.p.terminal.wallet.WalletManager
import cash.p.terminal.wallet.WalletStorage
import cash.p.terminal.wallet.providers.CryptoCompareProvider
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import cash.p.terminal.wallet.useCases.WalletUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val walletFeatureModule = module {
    singleOf(::WalletManager) bind IWalletManager::class
    singleOf(::AccountManager) bind IAccountManager::class
    singleOf(::WalletStorage) bind IWalletStorage::class
    singleOf(::MarketKitWrapper)
    singleOf(::SubscriptionManager)
    factoryOf(::WalletUseCase)
    factoryOf(::CryptoCompareProvider)

    singleOf(::GetHardwarePublicKeyForWalletUseCase)

    includes(mappersModule, useCasesModule)
}