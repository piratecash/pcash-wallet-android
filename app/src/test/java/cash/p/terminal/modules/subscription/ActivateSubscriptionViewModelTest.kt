package cash.p.terminal.modules.subscription

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmMessageSigning
import cash.p.terminal.core.managers.EvmSignerFactory
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.SubscriptionManager
import cash.p.terminal.wallet.models.SubscriptionResponse
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.reactivex.Single
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Hardware accounts (Tangem/Trezor) have no native private key: address resolution and signing
 * must go through [EvmSignerFactory]/[EvmMessageSigning] rather than [AccountType.evmAddress]/
 * [AccountType.sign], both of which return null for hardware accounts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivateSubscriptionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher))

    private val marketKit = mockk<MarketKitWrapper>()
    private val accountManager = mockk<IAccountManager>()
    private val subscriptionManager = mockk<SubscriptionManager>(relaxed = true)
    private val evmSignerFactory = mockk<EvmSignerFactory>()
    private val evmBlockchainManager = mockk<EvmBlockchainManager>()

    private val hardwareAccount = Account(
        id = "hw-1",
        name = "Tangem",
        type = AccountType.HardwareCard(
            cardId = "card1",
            backupCardsCount = 1,
            walletPublicKey = "pub",
            signedHashes = 0
        ),
        origin = AccountOrigin.Restored,
        level = 0
    )
    private val hardwareAddress = Address("0x" + "1".repeat(39) + "e")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { evmBlockchainManager.getChain(BlockchainType.Ethereum) } returns Chain.Ethereum
        every { accountManager.accounts } returns listOf(hardwareAccount)
        coEvery {
            evmSignerFactory.resolveAddress(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)
        } returns hardwareAddress
        every { marketKit.subscriptionsSingle(listOf(hardwareAddress.hex)) } returns
            Single.just(listOf(SubscriptionResponse(hardwareAddress.hex, deadline = 100L)))
        every { marketKit.authGetSignMessage(hardwareAddress.hex) } returns Single.just("sign me")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(EvmMessageSigning)
    }

    @Test
    fun sign_hardwareAccount_signsThroughEvmSignerFactoryAndEvmMessageSigning() = runTest(dispatcher) {
        val signer = mockk<Signer>()
        coEvery {
            evmSignerFactory.createSigner(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)
        } returns signer
        mockkObject(EvmMessageSigning)
        coEvery { EvmMessageSigning.signPersonalMessage(signer, any()) } returns byteArrayOf(1, 2, 3)
        every { marketKit.authenticate(any(), hardwareAddress.hex) } returns Single.just("token")

        val viewModel = ActivateSubscriptionViewModel(
            marketKit, accountManager, subscriptionManager, dispatcherProvider, evmSignerFactory, evmBlockchainManager
        )

        viewModel.sign()

        coVerify(exactly = 1) {
            evmSignerFactory.createSigner(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)
        }
        coVerify(exactly = 1) { EvmMessageSigning.signPersonalMessage(signer, "sign me".toByteArray()) }
        assertTrue(viewModel.uiState.fetchingTokenSuccess)
    }
}
