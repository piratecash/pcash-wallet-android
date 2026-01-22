package cash.p.terminal.core.di

import cash.p.terminal.di.managerModule
import cash.p.terminal.di.repositoryModule
import cash.p.terminal.di.storageModule
import cash.p.terminal.di.swapProvidersModule
import cash.p.terminal.di.viewModelModule
import cash.p.terminal.feature.logging.di.featureLoggingModule
import cash.p.terminal.feature.miniapp.di.featureMiniAppModule
import cash.p.terminal.featureStacking.di.featureStackingModule
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.premium.di.featurePremiumModule
import cash.p.terminal.tangem.di.featureTangemModule
import cash.p.terminal.wallet.di.walletFeatureModule
import org.koin.dsl.module

val appModule = module {
    includes(
        storageModule,
        managerModule,
        repositoryModule,
        viewModelModule,
        walletFeatureModule,
        featureStackingModule,
        featureTangemModule,
        networkModule,
        swapProvidersModule,
        contractValidatorModule,
        useCaseModule,
        appUpdateModule,
        featurePremiumModule,
        featureLoggingModule,
        featureMiniAppModule
    )
}