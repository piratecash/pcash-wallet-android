package cash.p.terminal.di

import cash.p.terminal.modules.multiswap.MultiSwapOnChainMonitor
import cash.p.terminal.modules.multiswap.MultiSwapRouteResolver
import cash.p.terminal.modules.multiswap.SwapQuoteService
import cash.p.terminal.modules.multiswap.providers.ChangeNowProvider
import cash.p.terminal.modules.multiswap.providers.ExolixProvider
import cash.p.terminal.modules.multiswap.providers.MayaProvider
import cash.p.terminal.modules.multiswap.providers.OffChainSwapProviderSupport
import cash.p.terminal.modules.multiswap.providers.QuickexProvider
import cash.p.terminal.modules.multiswap.providers.StonFiProvider
import cash.p.terminal.modules.multiswap.providers.SwapProviderTransactionFactory
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRegistry
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRepository
import cash.p.terminal.modules.multiswap.providers.ThorChainProvider
import cash.p.terminal.modules.multiswap.providers.ThorChainSwapStatusRepository
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val swapProvidersModule = module {
    factoryOf(::SwapQuoteService)
    singleOf(::SwapProviderTransactionFactory)
    singleOf(::OffChainSwapProviderSupport)

    singleOf(::ChangeNowProvider)
    singleOf(::QuickexProvider)
    singleOf(::ExolixProvider)
    singleOf(::StonFiProvider)

    single<SwapProviderTransactionStatusRepository>(named(SwapProvider.THORCHAIN)) {
        ThorChainSwapStatusRepository(ThorChainProvider.thornodeAPI)
    }
    single<SwapProviderTransactionStatusRepository>(named(SwapProvider.MAYA)) {
        ThorChainSwapStatusRepository(MayaProvider.thornodeAPI)
    }

    singleOf(::SwapProvidersRegistry)
    singleOf(::MultiSwapOnChainMonitor)
    singleOf(::MultiSwapRouteResolver)
    singleOf(::SwapProvidersRepository)
}
