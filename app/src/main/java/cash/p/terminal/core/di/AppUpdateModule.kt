package cash.p.terminal.core.di

import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import org.koin.dsl.module

val appUpdateModule = module {
    factory { AppUpdateManagerFactory.create(get()) }
}