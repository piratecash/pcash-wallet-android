package cash.p.terminal.core.usecase

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.ZcashRescanException
import cash.p.terminal.core.managers.AdapterManager
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.domain.usecase.ClearZCashWalletDataUseCase
import cash.p.terminal.domain.usecase.ZcashEraseResult
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class RescanZcashUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher))

    private val adapterManager = mockk<AdapterManager>()
    private val clearZCashWalletDataUseCase = mockk<ClearZCashWalletDataUseCase>()
    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>(relaxed = true)

    private val accountId = "acc-1"
    private val account = mockk<Account> { every { id } returns accountId }
    private val newHeight = 2_477_000L

    private val useCase = RescanZcashUseCase(
        adapterManager,
        clearZCashWalletDataUseCase,
        restoreSettingsManager,
        localStorage,
        dispatcherProvider,
    )

    /** Makes [AdapterManager.rescanZcashAccount] run the passed clearData block inline. */
    private fun wireRescanRunsClearData() {
        val clearData = slot<suspend () -> Unit>()
        coEvery { adapterManager.rescanZcashAccount(accountId, capture(clearData)) } coAnswers {
            clearData.captured.invoke()
        }
    }

    @Test
    fun invoke_eraseNone_abortsWithoutMutatingState() = runTest(dispatcher) {
        wireRescanRunsClearData()
        coEvery { clearZCashWalletDataUseCase(accountId) } returns ZcashEraseResult.NONE

        assertFailsWith<ZcashRescanException> { useCase(account, newHeight) }

        verify(exactly = 0) { localStorage.zcashAccountIds = any() }
        coVerify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    @Test
    fun invoke_eraseAll_persistsHeightAndRemovesId() = runTest(dispatcher) {
        wireRescanRunsClearData()
        coEvery { clearZCashWalletDataUseCase(accountId) } returns ZcashEraseResult.ALL
        val savedSettings = RestoreSettings()
        every { restoreSettingsManager.settings(account, BlockchainType.Zcash) } returns savedSettings
        every { localStorage.zcashAccountIds } returns setOf(accountId)

        useCase(account, newHeight)

        assertEquals(newHeight, savedSettings.birthdayHeight)
        verify { restoreSettingsManager.save(savedSettings, account, BlockchainType.Zcash) }
        verify { localStorage.zcashAccountIds = emptySet() }
    }

    @Test
    fun invoke_erasePartial_stillCommitsRescan() = runTest(dispatcher) {
        wireRescanRunsClearData()
        coEvery { clearZCashWalletDataUseCase(accountId) } returns ZcashEraseResult.PARTIAL
        every { restoreSettingsManager.settings(account, BlockchainType.Zcash) } returns RestoreSettings()
        every { localStorage.zcashAccountIds } returns setOf(accountId)

        useCase(account, newHeight)

        verify { restoreSettingsManager.save(any(), account, BlockchainType.Zcash) }
        verify { localStorage.zcashAccountIds = emptySet() }
    }
}
