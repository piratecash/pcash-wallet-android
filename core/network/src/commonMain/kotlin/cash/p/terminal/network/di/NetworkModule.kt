package cash.p.terminal.network.di

import SolanaRpcApiImpl
import cash.p.terminal.network.binance.api.BinanceApi
import cash.p.terminal.network.binance.api.BinanceApiImpl
import cash.p.terminal.network.binance.api.EthereumRpcApi
import cash.p.terminal.network.binance.api.EthereumRpcApiImpl
import cash.p.terminal.network.binance.api.SolanaRpcApi
import cash.p.terminal.network.binance.api.TonRpcApi
import cash.p.terminal.network.binance.api.TonRpcApiImpl
import cash.p.terminal.network.binance.api.TronRpcApi
import cash.p.terminal.network.binance.api.TronRpcApiImpl
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
    factoryOf(::EthereumRpcApiImpl) bind EthereumRpcApi::class
    factoryOf(::BinanceApiImpl) bind BinanceApi::class
    factoryOf(::SolanaRpcApiImpl) bind SolanaRpcApi::class
    factoryOf(::TronRpcApiImpl) bind TronRpcApi::class
    factoryOf(::TonRpcApiImpl) bind TonRpcApi::class

    includes(
        networkPirateModule,
        networkChangeNowModule,
        networkPirateNewsModule,
        databaseModule,
        decoderModule
    )
}