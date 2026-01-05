package cash.p.terminal.feature.logging.di

import cash.p.terminal.feature.logging.data.database.LoggingDatabase
import cash.p.terminal.feature.logging.data.repository.LoginRecordRepository
import cash.p.terminal.feature.logging.data.repository.LoginRecordStorage
import cash.p.terminal.feature.logging.detail.LoggingDetailViewModel
import cash.p.terminal.feature.logging.domain.usecase.DeleteLoggingOnDuressUseCase
import cash.p.terminal.feature.logging.domain.usecase.GetZecWalletsUseCase
import cash.p.terminal.feature.logging.domain.usecase.LogLoginAttemptUseCase
import cash.p.terminal.feature.logging.history.LoggingListViewModel
import cash.p.terminal.feature.logging.settings.LoggingSettingsViewModel
import io.horizontalsystems.core.ILoginRecordRepository
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val featureLoggingModule = module {
    // Database
    single { LoggingDatabase.create(get()) }
    single { get<LoggingDatabase>().loginRecordDao() }

    // Repository
    singleOf(::LoginRecordStorage)
    singleOf(::LoginRecordRepository) bind ILoginRecordRepository::class

    // Use Cases
    singleOf(::LogLoginAttemptUseCase)
    singleOf(::DeleteLoggingOnDuressUseCase)
    singleOf(::GetZecWalletsUseCase)

    // ViewModels
    viewModelOf(::LoggingSettingsViewModel)
    viewModelOf(::LoggingListViewModel)
    viewModelOf(::LoggingDetailViewModel)
}
