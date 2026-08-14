package cash.p.terminal.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.network.pirate.di.PREMIUM_API_BASE_URL_QUALIFIER
import cash.p.terminal.shared.PcashApp
import java.util.Locale
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val APPLICATION_NAME = "P.CASH"

fun main() {
    startKoin {
        modules(networkModule, desktopModule)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = APPLICATION_NAME,
        ) {
            PcashApp()
        }
    }
}

private val desktopModule = module {
    single<AppHeadersProvider> {
        object : AppHeadersProvider {
            override val appVersion = "desktop"
            override val currentLanguage: String
                get() = Locale.getDefault().language.ifEmpty { "en" }
            override val appSignature: String? = null
        }
    }
    single(named(PREMIUM_API_BASE_URL_QUALIFIER)) { "https://p.cash/api/" }
}
