package cash.p.terminal.core.di

import android.app.Application
import android.content.Context
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedDialog
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedViewModel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.mockk.mockk
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

class KoinGraphTest : KoinTest {

    @Test
    fun verifyKoinGraph() {
        val testOverrides = module {
            single<Context> { mockk() }
            single<Application> { mockk() }
            single<HttpClientEngine> { OkHttp.create() }
        }

        val fullModule = module {
            includes(testOverrides, appModule)
        }

        fullModule.verify(
            extraTypes = listOf(Application::class, Context::class, HttpClientEngine::class),
            injections = injectedParameters(
                definition<AccountTypeNotSupportedViewModel>(AccountTypeNotSupportedDialog.Input::class)
            )
        )
    }
}
