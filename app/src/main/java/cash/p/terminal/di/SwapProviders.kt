package cash.p.terminal.di

import cash.p.terminal.modules.multiswap.providers.ChangeNowProvider
import cash.p.terminal.modules.multiswap.providers.StonFiProvider
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val swapProvidersModule = module {
    factoryOf(::ChangeNowProvider)
    factoryOf(::StonFiProvider)
}