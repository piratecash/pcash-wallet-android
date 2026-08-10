package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.SecretString
import com.m2049r.xmrwallet.model.Wallet
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class MoneroDeviceWalletProvisionerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher))
    private val dao = mockk<MoneroFileDao>(relaxed = true)
    private val nativeWallet = mockk<MoneroDeviceWalletNative> {
        every { requireAvailable() } returns Unit
    }
    private val walletPublicKey = "wallet-key"
    private val account = Account(
        id = "account-id",
        name = "Trezor",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = walletPublicKey,
        ),
        origin = AccountOrigin.Created,
        level = 0,
    )
    private val restoreSettings = RestoreSettings()
    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true) {
        every { settings(account, BlockchainType.Monero) } returns restoreSettings
    }

    @Test
    fun provision_newWallet_persistsFilesAndHeight() = runTest(dispatcher) {
        var accountSaved = false
        coEvery { dao.getAssociatedRecord(account.id) } returns null
        coEvery { nativeWallet.create(any(), any(), HEIGHT, account) } coAnswers {
            createWalletFiles(firstArg())
            walletPublicKey
        }
        coEvery { dao.insert(any()) } coAnswers {
            assertTrue(accountSaved)
        }

        val result = createProvisioner().provision(account, HEIGHT) {
            accountSaved = true
        }

        assertEquals(walletPublicKey, result)
        assertEquals(HEIGHT, restoreSettings.birthdayHeight)
        verify(exactly = 1) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                account,
                MoneroSpentReconciliationState.LiveRefreshPending,
            )
        }
        coVerify(exactly = 1) {
            dao.insert(
                match {
                    it.accountId == account.id &&
                        it.fileName.value == BASE_NAME
                },
            )
        }
        assertTrue(walletFile().isFile)
        assertTrue(keysFile().isFile)
    }

    @Test
    fun provision_existingValidWallet_preservesRestoreHeight() = runTest(dispatcher) {
        createWalletFiles(walletFile())
        coEvery { dao.getAssociatedRecord(account.id) } returns record()
        restoreSettings.birthdayHeight = ORIGINAL_HEIGHT

        val result = createProvisioner().provision(account, HEIGHT)

        assertEquals(walletPublicKey, result)
        assertEquals(ORIGINAL_HEIGHT, restoreSettings.birthdayHeight)
        coVerify(exactly = 0) { nativeWallet.create(any(), any(), any(), any()) }
        verify(exactly = 0) {
            restoreSettingsManager.save(any(), any(), BlockchainType.Monero)
        }
    }

    @Test
    fun provision_existingValidWalletMissingHeight_restoresRequestedHeight() = runTest(dispatcher) {
        createWalletFiles(walletFile())
        coEvery { dao.getAssociatedRecord(account.id) } returns record()

        val result = createProvisioner().provision(account, HEIGHT)

        assertEquals(walletPublicKey, result)
        assertEquals(HEIGHT, restoreSettings.birthdayHeight)
        coVerify(exactly = 0) { nativeWallet.create(any(), any(), any(), any()) }
        verify(exactly = 1) {
            restoreSettingsManager.save(
                restoreSettings,
                account,
                BlockchainType.Monero,
            )
        }
    }

    @Test
    fun provision_nativeFailure_removesNewWalletFiles() = runTest(dispatcher) {
        coEvery { dao.getAssociatedRecord(account.id) } returns null
        coEvery { nativeWallet.create(any(), any(), HEIGHT, account) } coAnswers {
            createWalletFiles(firstArg())
            error("creation failed")
        }

        assertFailsWith<IllegalStateException> {
            createProvisioner().provision(account, HEIGHT)
        }

        assertFalse(walletFile().exists())
        assertFalse(keysFile().exists())
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun provision_nativeOwnershipRetained_preservesOpenWalletFiles() = runTest(dispatcher) {
        coEvery { dao.getAssociatedRecord(account.id) } returns null
        coEvery { nativeWallet.create(any(), any(), HEIGHT, account) } coAnswers {
            createWalletFiles(firstArg())
            throw MoneroDeviceWalletOwnershipRetainedException(
                IllegalStateException("close failed"),
            )
        }

        assertFailsWith<MoneroDeviceWalletOwnershipRetainedException> {
            createProvisioner().provision(account, HEIGHT)
        }

        assertTrue(walletFile().isFile)
        assertTrue(keysFile().isFile)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun provision_retryWhileNativeOwnershipRetained_preservesOpenWalletFiles() =
        runTest(dispatcher) {
            coEvery { dao.getAssociatedRecord(account.id) } returns null
            coEvery { nativeWallet.create(any(), any(), HEIGHT, account) } coAnswers {
                createWalletFiles(firstArg())
                throw MoneroDeviceWalletOwnershipRetainedException(
                    IllegalStateException("close failed"),
                )
            }
            val provisioner = createProvisioner()
            assertFailsWith<MoneroDeviceWalletOwnershipRetainedException> {
                provisioner.provision(account, HEIGHT)
            }
            every { nativeWallet.requireAvailable() } throws
                IllegalStateException("A Monero wallet is already open")

            assertFailsWith<IllegalStateException> {
                provisioner.provision(account, HEIGHT)
            }

            assertTrue(walletFile().isFile)
            assertTrue(keysFile().isFile)
            coVerify(exactly = 1) { nativeWallet.create(any(), any(), any(), any()) }
        }

    @Test
    fun useMoneroDeviceWallet_closeFails_marksNativeOwnershipRetained() {
        val operationFailure = IllegalStateException("creation failed")
        val closeFailure = HardwareWalletOperationException(
            HardwareWalletErrorCode.Disconnected,
            "device disconnected",
        )

        val failure = assertFailsWith<MoneroDeviceWalletOwnershipRetainedException> {
            useMoneroDeviceWallet(
                close = { false },
                closeFailure = { closeFailure },
            ) {
                throw operationFailure
            }
        }

        assertSame(operationFailure, failure.cause)
        assertEquals(listOf(closeFailure), operationFailure.suppressed.toList())
    }

    @Test
    fun useMoneroDeviceWallet_successCloseFailure_usesTypedFailureAsCause() {
        val closeFailure = HardwareWalletOperationException(
            HardwareWalletErrorCode.Cancelled,
            "cancelled",
        )

        val failure = assertFailsWith<MoneroDeviceWalletOwnershipRetainedException> {
            useMoneroDeviceWallet(
                close = { false },
                closeFailure = { closeFailure },
            ) {
                "created"
            }
        }

        assertSame(closeFailure, failure.cause)
    }

    @Test
    fun hardwareWalletCloseFailure_cancelledStatus_preservesCodeAndDetail() {
        val status = mockk<Wallet.Status> {
            every { hardwareWalletError } returns HardwareWalletErrorCode.Cancelled
            every { errorString } returns "cancelled on device"
        }

        val failure = status.toHardwareWalletCloseFailure("fallback")

        assertEquals(HardwareWalletErrorCode.Cancelled, failure.error)
        assertEquals("cancelled on device", failure.message)
    }

    @Test
    fun hardwareWalletCloseFailure_missingStatus_usesProtocolFallback() {
        val failure = (null as Wallet.Status?).toHardwareWalletCloseFailure("close failed")

        assertEquals(HardwareWalletErrorCode.Protocol, failure.error)
        assertEquals("close failed", failure.message)
    }

    @Test
    fun storeMoneroWalletSafely_nativeFault_abandonsBeforeThrowing() {
        var abandoned = false

        val error = assertFailsWith<HardwareWalletOperationException> {
            storeMoneroWalletSafely(
                failureMessage = "store failed",
                store = { MONERO_STORE_NATIVE_FAULT },
                onNativeFault = { abandoned = true },
            )
        }

        assertTrue(abandoned)
        assertEquals(HardwareWalletErrorCode.StoreFailed, error.error)
    }

    @Test
    fun storeMoneroWalletSafely_regularFailure_doesNotAbandon() {
        var abandoned = false

        assertFailsWith<HardwareWalletOperationException> {
            storeMoneroWalletSafely(
                failureMessage = "store failed",
                store = { 1 },
                onNativeFault = { abandoned = true },
            )
        }

        assertFalse(abandoned)
    }

    private fun createProvisioner() = MoneroDeviceWalletProvisioner(
        moneroFileDao = dao,
        nativeWallet = nativeWallet,
        nativeRuntime = object : MoneroNativeWalletRuntime {
            override suspend fun <T> withExclusiveWallet(block: suspend () -> T): T = block()
        },
        files = MoneroDeviceWalletFileStore.create(temporaryFolder.root, PASSWORD),
        restoreSettingsManager = restoreSettingsManager,
        dispatcherProvider = dispatcherProvider,
    )

    private fun record() = MoneroFileRecord(
        accountId = account.id,
        fileName = SecretString(BASE_NAME),
        password = SecretString(PASSWORD),
    )

    private fun createWalletFiles(walletFile: File) {
        walletFile.writeText("wallet")
        File(walletFile.parentFile, "${walletFile.name}.keys").writeText("keys")
    }

    private fun walletFile() = temporaryFolder.root.resolve(BASE_NAME)

    private fun keysFile() = temporaryFolder.root.resolve("$BASE_NAME.keys")

    private companion object {
        const val BASE_NAME = "trezor-account-id"
        const val PASSWORD = "password"
        const val ORIGINAL_HEIGHT = 3_000_000L
        const val HEIGHT = 3_529_956L
    }
}
