package cash.p.terminal.network.unstoppable.di

import cash.p.terminal.network.unstoppable.api.UnstoppableApi
import cash.p.terminal.network.unstoppable.data.mapper.UnstoppableMapper
import cash.p.terminal.network.unstoppable.data.repository.UnstoppableRepositoryImpl
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val networkUnstoppableModule = module {
    singleOf(::UnstoppableApi)
    singleOf(::UnstoppableRepositoryImpl) bind UnstoppableRepository::class
    singleOf(::UnstoppableMapper)
}
