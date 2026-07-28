package cash.p.terminal.tangem.domain.sdk

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import com.tangem.TangemSdk
import io.horizontalsystems.core.DispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

class CardSdkConfigRepositoryTest {

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val sdk: TangemSdk = mockk()
    private val cardSdkProvider: CardSdkProvider = mockk {
        every { sdk } returns this@CardSdkConfigRepositoryTest.sdk
    }
    private val accountManager: IAccountManager = mockk {
        every { accounts } returns listOf(
            mockk<Account> {
                every { type } returns AccountType.HardwareCard("", 0, "", 0)
            }
        )
    }
    private val dispatcherProvider: DispatcherProvider = mockk {
        every { applicationScope } returns this@CardSdkConfigRepositoryTest.applicationScope
    }

    private lateinit var repository: CardSdkConfigRepository

    @Before
    fun setUp() {
        repository = CardSdkConfigRepository(
            cardSdkProvider = cardSdkProvider,
            dispatcherProvider = dispatcherProvider,
            accountManager = accountManager,
        )
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
        dispatcher.close()
    }

    @Test
    fun disableReaderModeForQrScanner_restoreAlreadyEnabling_disablesAfterEnableCompletes() {
        val enableStarted = CountDownLatch(1)
        val finishEnable = CountDownLatch(1)
        val disableAfterEnable = CountDownLatch(1)
        val enableCompleted = AtomicBoolean(false)

        every { sdk.forceEnableReaderMode() } answers {
            enableStarted.countDown()
            assertTrue(finishEnable.await(2, TimeUnit.SECONDS))
            enableCompleted.set(true)
        }
        every { sdk.forceDisableReaderMode() } answers {
            if (enableCompleted.get()) disableAfterEnable.countDown()
        }

        repository.restoreReaderModeAfterQrScanner()
        assertTrue(enableStarted.await(2, TimeUnit.SECONDS), "Reader mode restore did not start")

        repository.disableReaderModeForQrScanner()
        finishEnable.countDown()

        assertTrue(
            disableAfterEnable.await(1, TimeUnit.SECONDS),
            "In-flight restore enabled reader mode after the scanner disabled it",
        )
    }
}
