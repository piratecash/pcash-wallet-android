package cash.p.terminal.modules.paycore

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.DispatcherProvider
import io.ktor.client.HttpClient
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class PayCoreModuleTest {

    @Test
    fun payCoreApiService_moduleResolution_resolvesSignatureHelperWithDefaultClock() {
        val dispatcher = UnconfinedTestDispatcher()
        val app = koinApplication {
            modules(
                module {
                    single<IAccountManager> { mockk(relaxed = true) }
                    single<HttpClient> { mockk(relaxed = true) }
                    single<DispatcherProvider> {
                        TestDispatcherProvider(
                            dispatcher = dispatcher,
                            applicationScope = TestScope(dispatcher),
                        )
                    }
                },
                payCoreModule,
            )
        }

        val apiService = app.koin.get<PayCoreApiService>()

        assertNotNull(apiService)
        app.close()
    }
}
