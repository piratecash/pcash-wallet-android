package cash.p.terminal.network.github.di

import cash.p.terminal.network.github.api.GithubApi
import cash.p.terminal.network.github.data.mapper.GithubReleaseMapper
import cash.p.terminal.network.github.data.repository.AppUpdateRepositoryImpl
import cash.p.terminal.network.github.domain.repository.AppUpdateRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val networkGithubModule = module {
    singleOf(::GithubApi)
    singleOf(::GithubReleaseMapper)
    singleOf(::AppUpdateRepositoryImpl) bind AppUpdateRepository::class
}
