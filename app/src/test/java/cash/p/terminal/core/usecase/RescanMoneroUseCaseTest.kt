package cash.p.terminal.core.usecase

import cash.p.terminal.core.MoneroRescanException
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class RescanMoneroUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher))
    private val moneroKitManager = mockk<MoneroKitManager>(relaxed = true)
    private val removeMoneroWalletFilesUseCase = mockk<RemoveMoneroWalletFilesUseCase>(relaxed = true)
    private val moneroFileDao = mockk<MoneroFileDao>(relaxed = true)
    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)

    private val account = Account(
        id = "account-id",
        name = "Monero",
        type = AccountType.MnemonicMonero(
            words = emptyList(),
            password = "password",
            height = 1,
            walletInnerName = "wallet"
        ),
        origin = AccountOrigin.Created,
        level = 0
    )

    private val useCase = RescanMoneroUseCase(
        moneroKitManager = moneroKitManager,
        removeMoneroWalletFilesUseCase = removeMoneroWalletFilesUseCase,
        moneroFileDao = moneroFileDao,
        restoreSettingsManager = restoreSettingsManager,
        offlineModeManager = offlineModeManager,
        dispatcherProvider = dispatcherProvider,
    )

    private fun givenOffline(offline: Boolean) {
        every {
            offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Monero))
        } returns offline
    }

    private fun givenActiveAccount(active: Boolean) {
        coEvery { moneroKitManager.rescanIfActive(account, HEIGHT) } returns active
    }

    @Test
    fun invoke_activeAccount_delegatesToManagerWithoutTouchingWalletData() = runTest(dispatcher) {
        givenOffline(false)
        givenActiveAccount(true)

        useCase(account, HEIGHT)

        coVerify(exactly = 1) { moneroKitManager.rescanIfActive(account, HEIGHT) }
        coVerify(exactly = 0) { removeMoneroWalletFilesUseCase(any<Account>()) }
        coVerify(exactly = 0) { moneroFileDao.deleteAssociatedRecord(any()) }
        coVerify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    @Test
    fun invoke_inactiveAccount_removesWalletDataThenPersistsHeight() = runTest(dispatcher) {
        givenOffline(false)
        givenActiveAccount(false)
        val restoreSettings = RestoreSettings()
        every { restoreSettingsManager.settings(account, BlockchainType.Monero) } returns restoreSettings
        coEvery { removeMoneroWalletFilesUseCase(account) } returns true

        useCase(account, HEIGHT)

        coVerifyOrder {
            removeMoneroWalletFilesUseCase(account)
            moneroFileDao.deleteAssociatedRecord(account.id)
            restoreSettingsManager.save(restoreSettings, account, BlockchainType.Monero)
        }
        assertEquals(HEIGHT, restoreSettings.birthdayHeight)
    }

    @Test
    fun invoke_inactiveAccountFileRemovalFails_keepsRecordAndHeight() = runTest(dispatcher) {
        givenOffline(false)
        givenActiveAccount(false)
        coEvery { removeMoneroWalletFilesUseCase(account) } returns false

        assertFailsWith<MoneroRescanException> { useCase(account, HEIGHT) }

        coVerify(exactly = 0) { moneroFileDao.deleteAssociatedRecord(any()) }
        coVerify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    @Test
    fun invoke_offlinePair_throwsWithoutRemovingWalletFiles() = runTest(dispatcher) {
        givenOffline(true)

        assertFailsWith<MoneroRescanException> { useCase(account, HEIGHT) }

        coVerify(exactly = 0) { moneroKitManager.rescanIfActive(any(), any()) }
        coVerify(exactly = 0) { removeMoneroWalletFilesUseCase(any<Account>()) }
        coVerify(exactly = 0) { moneroFileDao.deleteAssociatedRecord(any()) }
        coVerify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    private companion object {
        const val HEIGHT = 2_975_499L
    }
}
