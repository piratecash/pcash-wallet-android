package cash.p.terminal.network.di

import android.content.Context
import androidx.room.Room
import cash.p.terminal.network.data.NetworkEnvironment
import cash.p.terminal.network.data.isAppDebuggable
import cash.p.terminal.network.pirate.data.database.CACHE_DATABASE_NAME
import cash.p.terminal.network.pirate.data.database.CacheAppDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformNetworkModule(): Module = module {
    single {
        val context = get<Context>()
        NetworkEnvironment(
            applicationId = context.packageName,
            isDebug = context.isAppDebuggable(),
        )
    }
    single {
        Room.databaseBuilder(
            get(),
            CacheAppDatabase::class.java,
            CACHE_DATABASE_NAME,
        ).build()
    }
}
