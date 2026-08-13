package cash.p.terminal.network.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import cash.p.terminal.network.data.NetworkEnvironment
import cash.p.terminal.network.pirate.data.database.CACHE_DATABASE_NAME
import cash.p.terminal.network.pirate.data.database.CacheAppDatabase
import cash.p.terminal.network.pirate.data.database.CacheAppDatabaseConstructor
import java.io.File
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformNetworkModule(): Module = module {
    single {
        NetworkEnvironment(
            applicationId = "cash.p.terminal",
            isDebug = false,
        )
    }
    single {
        Room.databaseBuilder<CacheAppDatabase>(
            name = desktopCacheDatabaseFile().absolutePath,
            factory = CacheAppDatabaseConstructor::initialize,
        )
            .setDriver(BundledSQLiteDriver())
            .build()
    }
}

private fun desktopCacheDatabaseFile(): File {
    val userHome = checkNotNull(System.getProperty("user.home"))
    val osName = System.getProperty("os.name").orEmpty()
    val dataDirectory = when {
        osName.startsWith("Mac", ignoreCase = true) ->
            File(userHome, "Library/Application Support/p.cash")
        osName.startsWith("Windows", ignoreCase = true) ->
            File(System.getenv("APPDATA") ?: userHome, "p.cash")
        else ->
            File(System.getenv("XDG_DATA_HOME") ?: "$userHome/.local/share", "p.cash")
    }
    check(dataDirectory.isDirectory || dataDirectory.mkdirs()) {
        "Unable to create application data directory: $dataDirectory"
    }
    return File(dataDirectory, CACHE_DATABASE_NAME)
}
