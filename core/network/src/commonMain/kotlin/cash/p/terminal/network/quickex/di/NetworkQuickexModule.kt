package cash.p.terminal.network.quickex.di

import cash.p.terminal.network.quickex.api.QuickexApi
import cash.p.terminal.network.quickex.data.mapper.QuickexMapper
import cash.p.terminal.network.quickex.data.repository.QuickexRepositoryImpl
import cash.p.terminal.network.quickex.domain.repository.QuickexRepository
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val networkQuickexModule = module {
    factoryOf(::QuickexApi)
    factoryOf(::QuickexRepositoryImpl) bind QuickexRepository::class
    factoryOf(::QuickexMapper)
}