package cash.p.terminal.network.di

import cash.p.terminal.network.binance.api.BinanceApi
import cash.p.terminal.network.binance.api.BinanceApiImpl
import cash.p.terminal.network.binance.api.EthereumRpcApi
import cash.p.terminal.network.changenow.di.networkChangeNowModule
import cash.p.terminal.network.data.buildNetworkClient
import cash.p.terminal.network.pirate.di.networkPirateModule
import cash.p.terminal.network.piratenews.di.networkPirateNewsModule
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val networkModule = module {
    single { buildNetworkClient() }

    // API
    factoryOf(::EthereumRpcApi)
    factoryOf(::BinanceApiImpl) bind BinanceApi::class

    includes(
        networkPirateModule,
        networkChangeNowModule,
        networkPirateNewsModule,
        databaseModule,
        decoderModule
    )
}