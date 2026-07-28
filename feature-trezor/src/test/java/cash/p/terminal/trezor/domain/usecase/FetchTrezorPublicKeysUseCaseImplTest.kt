package cash.p.terminal.trezor.domain.usecase

import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorClientSession
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorKeyResult
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FetchTrezorPublicKeysUseCaseImplTest {

    // Fake ITrezorClient whose connect(block) runs the block on a mocked TrezorClientSession, so the
    // use case exercises the real connect() path and we can stub getFeatures/getPublicKeys.
    private val session: TrezorClientSession = mockk()
    private val trezorClient = object : ITrezorClient {
        override suspend fun <T> connect(block: suspend TrezorClientSession.() -> T): T = session.block()
    }
    private val accountManager: IAccountManager = mockk(relaxed = true)

    private val useCase = FetchTrezorPublicKeysUseCaseImpl(trezorClient, accountManager)

    @Test
    fun invoke_storedModelUnknownDeviceReportsT2B1SameDevice_updatesAccountModel() {
        runFetch(stored = trezorDevice(model = "unknown"), features = features(internalModel = "T2B1"))

        verify(exactly = 1) {
            accountManager.update(
                match {
                    it.id == ACCOUNT_ID &&
                        (it.type as AccountType.TrezorDevice).model == "T2B1" &&
                        (it.type as AccountType.TrezorDevice).deviceId == DEVICE_ID
                }
            )
        }
    }

    @Test
    fun invoke_storedModelAlreadyResolvableSafe3_doesNotUpdate() {
        runFetch(stored = trezorDevice(model = "T3B1"), features = features(internalModel = "T2B1"))

        verify(exactly = 0) { accountManager.update(any()) }
    }

    @Test
    fun invoke_storedModelAlreadyResolvableModelT_doesNotUpdate() {
        runFetch(stored = trezorDevice(model = "T2T1"), features = features(internalModel = "T2B1"))

        verify(exactly = 0) { accountManager.update(any()) }
    }

    @Test
    fun invoke_deviceReportsUnrecognizedModel_doesNotUpdate() {
        runFetch(stored = trezorDevice(model = "unknown"), features = features(internalModel = "ZZZZ"))

        verify(exactly = 0) { accountManager.update(any()) }
    }

    @Test
    fun invoke_deviceReportsNullInternalModel_doesNotUpdate() {
        runFetch(stored = trezorDevice(model = "unknown"), features = features(internalModel = null))

        verify(exactly = 0) { accountManager.update(any()) }
    }

    @Test
    fun invoke_deviceIdMismatch_doesNotUpdate() {
        runFetch(
            stored = trezorDevice(model = "unknown"),
            features = features(deviceId = "other-device", internalModel = "T2B1")
        )

        verify(exactly = 0) { accountManager.update(any()) }
    }

    @Test
    fun invoke_liveDeviceIdNull_doesNotUpdate() {
        runFetch(
            stored = trezorDevice(model = "unknown"),
            features = features(deviceId = null, internalModel = "T2B1")
        )

        verify(exactly = 0) { accountManager.update(any()) }
    }

    @Test
    fun invoke_normalDerivation_callsGetFeaturesExactlyOnce() {
        runFetch(
            stored = trezorDevice(model = "T3T1"),
            features = features(internalModel = "T3T1"),
            tokenQueries = listOf(TokenQuery(BlockchainType.Solana, TokenType.Native)),
            keyResults = listOf(solanaResult())
        )

        coVerify(exactly = 1) { session.getFeatures() }
    }

    @Test
    fun invoke_returnsDerivedPublicKeys_unchangedByHeal() {
        val result = runFetch(
            stored = trezorDevice(model = "T3T1"),
            features = features(internalModel = "T3T1"),
            tokenQueries = listOf(TokenQuery(BlockchainType.Solana, TokenType.Native)),
            keyResults = listOf(solanaResult())
        )

        assertEquals(1, result.size)
        assertEquals(BlockchainType.Solana.uid, result.first().blockchainType)
        assertEquals("SolAddr", result.first().key.value)
        verify(exactly = 0) { accountManager.update(any()) }
    }

    private fun runFetch(
        stored: AccountType.TrezorDevice,
        features: TrezorFeatures,
        tokenQueries: List<TokenQuery> = emptyList(),
        keyResults: List<TrezorKeyResult> = emptyList()
    ): List<HardwarePublicKey> {
        every { accountManager.account(ACCOUNT_ID) } returns account(stored)
        coEvery { session.getFeatures() } returns features
        if (keyResults.isNotEmpty()) {
            coEvery { session.getPublicKeys(any()) } returns keyResults
        }
        return runBlocking { useCase.invoke(tokenQueries, ACCOUNT_ID) }
    }

    private fun account(type: AccountType.TrezorDevice) = Account(
        id = ACCOUNT_ID,
        name = "Trezor",
        type = type,
        origin = AccountOrigin.Restored,
        level = 0
    )

    private fun trezorDevice(model: String, deviceId: String = DEVICE_ID) = AccountType.TrezorDevice(
        deviceId = deviceId,
        model = model,
        firmwareVersion = "2.8.7",
        walletPublicKey = "wallet-public-key"
    )

    private fun features(deviceId: String? = DEVICE_ID, internalModel: String?) = TrezorFeatures(
        deviceId = deviceId,
        model = "Trezor Safe 3",
        internalModel = internalModel,
        firmwareVersion = "2.8.7",
        passphraseProtection = false
    )

    private fun solanaResult() =
        TrezorKeyResult(key = "SolAddr", publicKey = byteArrayOf(1, 2, 3), chainCode = ByteArray(0))

    companion object {
        private const val ACCOUNT_ID = "acc-1"
        private const val DEVICE_ID = "dev-1"
    }
}
