package cash.p.terminal.network.stonfi.di

import cash.p.terminal.network.stonfi.api.StonFiApi
import cash.p.terminal.network.stonfi.data.mapper.StonFiMapper
import cash.p.terminal.network.stonfi.data.repository.StonFiRepositoryImpl
import cash.p.terminal.network.stonfi.domain.repository.StonFiRepository
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val networkStonFiModule = module {
    factoryOf(::StonFiApi)
    factoryOf(::StonFiRepositoryImpl) bind StonFiRepository::class
    factoryOf(::StonFiMapper)
}
