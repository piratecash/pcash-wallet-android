package cash.p.terminal.core.usecase

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.MoneroDeviceWalletProvisioner
import cash.p.terminal.core.managers.MoneroTrezorReadiness
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezor.domain.usecase.TrezorMoneroRestoreHeightProvider
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorClientSession
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorInputScriptType
import cash.p.terminal.trezorkit.client.TrezorKeyResult
import cash.p.terminal.trezorkit.client.TrezorPublicKeyRequest
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAccountsStorage
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTrezorWalletUseCaseTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val accountsStorage = mockk<IAccountsStorage>(relaxed = true)
    private val hardwarePublicKeyStorage = mockk<IHardwarePublicKeyStorage>(relaxed = true)
    private val accountFactory = mockk<IAccountFactory>()
    private val walletActivator = mockk<WalletActivator>(relaxed = true)
    private val readiness = mockk<MoneroTrezorReadiness> {
        every { requireSupported(any()) } just Runs
    }
    private val provisioner = mockk<MoneroDeviceWalletProvisioner>()
    private val restoreSettingsManager = mockk<RestoreSettingsManager> {
        every { trezorMoneroRestoreHeight(any()) } returns null
    }
    private val heightProvider = mockk<TrezorMoneroRestoreHeightProvider> {
        coEvery { getRestoreHeight() } returns RESTORE_HEIGHT
    }

    @Test
    fun invoke_safe5Supported_provisionsAndActivatesMonero() = runTest(dispatcher) {
        val account = account(TrezorModel.Safe5)
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns account
        coEvery {
            provisioner.provision(account, RESTORE_HEIGHT, any())
        } coAnswers {
            thirdArg<() -> Unit>().invoke()
            "public-key"
        }

        val type = useCase(features(TrezorModel.Safe5))("Trezor", heightProvider)

        assertEquals(TrezorModel.Safe5.ids.single(), type.model)
        verify(exactly = 1) { readiness.requireSupported(any()) }
        verify(exactly = 1) { accountManager.save(account, false) }
        coVerify(exactly = 1) { hardwarePublicKeyStorage.save(any()) }
        coVerify(exactly = 1) {
            walletActivator.activateWalletsSuspended(
                account,
                match { queries ->
                    queries.count { it.blockchainType == BlockchainType.Monero } == 1
                },
            )
        }
        verify(exactly = 1) { accountManager.setActiveAccountId(account.id) }
        coVerify(exactly = 1) { heightProvider.getRestoreHeight() }
    }

    @Test
    fun invoke_safe5WithSavedHeight_skipsPromptAndUsesSavedHeight() = runTest(dispatcher) {
        val account = account(TrezorModel.Safe5)
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns account
        every {
            restoreSettingsManager.trezorMoneroRestoreHeight("public-key")
        } returns SAVED_RESTORE_HEIGHT
        coEvery {
            provisioner.provision(account, SAVED_RESTORE_HEIGHT, any())
        } coAnswers {
            thirdArg<() -> Unit>().invoke()
            "public-key"
        }

        useCase(features(TrezorModel.Safe5))("Trezor", heightProvider)

        coVerify(exactly = 0) { heightProvider.getRestoreHeight() }
        coVerify(exactly = 1) {
            provisioner.provision(account, SAVED_RESTORE_HEIGHT, any())
        }
    }

    @Test
    fun invoke_safe5WithLegacyDeletedAccount_reusesItsHeightWithoutPrompt() =
        runTest(dispatcher) {
            val account = account(TrezorModel.Safe5)
            val deletedAccount = account.copy(id = "deleted-account-id")
            every { accountFactory.account(any(), any(), any(), any(), any()) } returns account
            every { accountManager.getDeletedAccountIds() } returns listOf(deletedAccount.id)
            every { accountsStorage.loadAccount(deletedAccount.id) } returns deletedAccount
            every {
                restoreSettingsManager.settings(deletedAccount, BlockchainType.Monero)
            } returns RestoreSettings().apply {
                birthdayHeight = SAVED_RESTORE_HEIGHT
            }
            coEvery {
                provisioner.provision(account, SAVED_RESTORE_HEIGHT, any())
            } coAnswers {
                thirdArg<() -> Unit>().invoke()
                "public-key"
            }

            useCase(features(TrezorModel.Safe5))("Trezor", heightProvider)

            coVerify(exactly = 0) { heightProvider.getRestoreHeight() }
            coVerify(exactly = 1) {
                provisioner.provision(account, SAVED_RESTORE_HEIGHT, any())
            }
        }

    @Test
    fun invoke_safe5WithInvalidLegacyHeight_asksForRestoreHeight() =
        runTest(dispatcher) {
            val account = account(TrezorModel.Safe5)
            val deletedAccount = account.copy(id = "deleted-account-id")
            every { accountFactory.account(any(), any(), any(), any(), any()) } returns account
            every { accountManager.getDeletedAccountIds() } returns listOf(deletedAccount.id)
            every { accountsStorage.loadAccount(deletedAccount.id) } returns deletedAccount
            every {
                restoreSettingsManager.settings(deletedAccount, BlockchainType.Monero)
            } returns RestoreSettings().apply {
                birthdayHeight = -1
            }
            coEvery {
                provisioner.provision(account, RESTORE_HEIGHT, any())
            } coAnswers {
                thirdArg<() -> Unit>().invoke()
                "public-key"
            }

            useCase(features(TrezorModel.Safe5))("Trezor", heightProvider)

            coVerify(exactly = 1) { heightProvider.getRestoreHeight() }
            coVerify(exactly = 1) {
                provisioner.provision(account, RESTORE_HEIGHT, any())
            }
        }

    @Test
    fun invoke_safe5FailsBeforeAccountSave_leavesNoPersistedAccount() = runTest(dispatcher) {
        val account = account(TrezorModel.Safe5)
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns account
        coEvery {
            provisioner.provision(account, RESTORE_HEIGHT, any())
        } throws TrezorCancelledException()

        assertFailsWith<TrezorCancelledException> {
            useCase(features(TrezorModel.Safe5))("Trezor", heightProvider)
        }

        verify(exactly = 0) { accountManager.save(account, false) }
        coVerify(exactly = 0) { accountManager.delete(account.id) }
        coVerify(exactly = 0) { hardwarePublicKeyStorage.save(any()) }
    }

    @Test
    fun invoke_safe5FailsAfterAccountSave_removesPersistedAccount() = runTest(dispatcher) {
        val account = account(TrezorModel.Safe5)
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns account
        coEvery {
            provisioner.provision(account, RESTORE_HEIGHT, any())
        } coAnswers {
            thirdArg<() -> Unit>().invoke()
            error("record insert failed")
        }

        assertFailsWith<IllegalStateException> {
            useCase(features(TrezorModel.Safe5))("Trezor", heightProvider)
        }

        verify(exactly = 1) { accountManager.save(account, false) }
        coVerify(exactly = 1) { accountManager.delete(account.id) }
    }

    @Test
    fun invoke_modelT_usesExistingPublicKeyFlow() = runTest(dispatcher) {
        val account = account(TrezorModel.ModelT)
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns account

        useCase(features(TrezorModel.ModelT))("Trezor", heightProvider)

        verify(exactly = 0) { readiness.requireSupported(any()) }
        coVerify(exactly = 0) { provisioner.provision(any(), any(), any()) }
        verify(exactly = 1) { accountManager.save(account, false) }
        coVerify(exactly = 1) { hardwarePublicKeyStorage.save(any()) }
        coVerify(exactly = 1) { walletActivator.activateWalletsSuspended(account, any()) }
        coVerify(exactly = 0) { heightProvider.getRestoreHeight() }
    }

    private fun useCase(features: TrezorFeatures): CreateTrezorWalletUseCase {
        val session = mockk<TrezorClientSession> {
            coEvery { getFeatures() } returns features
            coEvery { getPublicKeys(any()) } answers {
                firstArg<List<TrezorPublicKeyRequest>>().map(::keyResult)
            }
        }
        return CreateTrezorWalletUseCase(
            trezorClient = TestTrezorClient(session),
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            accountDependencies = TrezorAccountDependencies(
                accountManager, accountsStorage, hardwarePublicKeyStorage, accountFactory, walletActivator,
            ),
            moneroDependencies = TrezorMoneroDependencies(readiness, provisioner, restoreSettingsManager),
        )
    }

    private fun account(model: TrezorModel) = Account(
        id = "account-id",
        name = "Trezor",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = model.ids.first(),
            firmwareVersion = "2.8.10",
            walletPublicKey = "public-key",
        ),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = false,
    )

    private fun features(model: TrezorModel) = TrezorFeatures(
        deviceId = "device-id",
        model = model.displayName,
        internalModel = model.ids.first(),
        firmwareVersion = "2.8.10",
        passphraseProtection = false,
        initialized = true,
        supportsTron = false,
        supportsMonero = model == TrezorModel.Safe5,
    )

    private fun keyResult(request: TrezorPublicKeyRequest): TrezorKeyResult {
        val key = if (request is TrezorPublicKeyRequest.Bitcoin) {
            HDExtendedKey(SEED, request.scriptType.purpose).serializePublic()
        } else {
            "public-key"
        }
        return TrezorKeyResult(
            key = key,
            publicKey = byteArrayOf(1, 2, 3),
            chainCode = byteArrayOf(4, 5, 6),
        )
    }

    private val TrezorInputScriptType.purpose: HDWallet.Purpose
        get() = when (this) {
            TrezorInputScriptType.SPENDADDRESS -> HDWallet.Purpose.BIP44
            TrezorInputScriptType.SPENDP2SHWITNESS -> HDWallet.Purpose.BIP49
            TrezorInputScriptType.SPENDWITNESS -> HDWallet.Purpose.BIP84
            TrezorInputScriptType.SPENDTAPROOT -> HDWallet.Purpose.BIP86
        }

    private class TestTrezorClient(
        private val session: TrezorClientSession,
    ) : ITrezorClient {
        override suspend fun <T> connect(
            block: suspend TrezorClientSession.() -> T,
        ): T = session.block()
    }

    private companion object {
        const val RESTORE_HEIGHT = 3_529_956L
        const val SAVED_RESTORE_HEIGHT = 3_400_000L
        val SEED = ByteArray(32) { (it + 1).toByte() }
    }
}
