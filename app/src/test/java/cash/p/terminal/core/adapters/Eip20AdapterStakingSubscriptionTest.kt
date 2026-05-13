package cash.p.terminal.core.adapters

import android.content.Context
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.StackingManager
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.ethereumkit.core.storage.TransactionSyncSourceStorage
import io.horizontalsystems.ethereumkit.models.Address
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class Eip20AdapterStakingSubscriptionTest {

    private val context = mockk<Context>(relaxed = true)
    private val evmTransactionRepository = mockk<EvmTransactionRepository>()
    private val coinManager = mockk<ICoinManager>(relaxed = true)
    private val evmLabelManager = mockk<EvmLabelManager>(relaxed = true)
    private val stackingManager = mockk<StackingManager>(relaxed = true)
    private val eip20Kit = mockk<Erc20Kit>(relaxed = true)
    private val repositoryReceiveAddress = mockk<Address>()

    private val wallet: Wallet = WalletFactory.previewStakingWallet()
    private val nonStakingWallet: Wallet = WalletFactory.previewWallet()
    private val receiveAddress = "0x0000000000000000000000000000000000000001"

    private fun stubCommon(balanceProcessor: PublishProcessor<BigInteger>) {
        every { evmTransactionRepository.transactionSyncSourceStorage } returns mockk<TransactionSyncSourceStorage>(relaxed = true)
        every { evmTransactionRepository.buildErc20Kit(context, any()) } returns eip20Kit
        every { repositoryReceiveAddress.eip55 } returns receiveAddress
        every { evmTransactionRepository.receiveAddress } returns repositoryReceiveAddress
        every { stackingManager.unpaidFlow(any()) } returns MutableStateFlow(BigDecimal.ZERO)
        every { eip20Kit.balanceFlowable } returns balanceProcessor
        every { eip20Kit.balance } returns BigInteger("100000000") // = 1.0 (decimal = 8)
    }

    private fun createAdapter(
        dispatcher: CoroutineDispatcher,
        wallet: Wallet = this.wallet,
    ) = Eip20Adapter(
        context = context,
        evmTransactionRepository = evmTransactionRepository,
        contractAddress = receiveAddress,
        baseToken = wallet.token,
        coinManager = coinManager,
        wallet = wallet,
        evmLabelManager = evmLabelManager,
        stackingManager = stackingManager,
        coroutineDispatcher = dispatcher,
    )

    private fun bigDecimalEq(expected: BigDecimal): (BigDecimal?) -> Boolean =
        { it != null && it.compareTo(expected) == 0 }

    @Test
    fun start_reloadsStaking_onEachOnChainBalanceEmission() = runTest {
        val balanceProcessor = PublishProcessor.create<BigInteger>()
        stubCommon(balanceProcessor)

        val adapter = createAdapter(UnconfinedTestDispatcher(testScheduler))
        adapter.start()

        verify(exactly = 1) {
            stackingManager.loadInvestmentData(
                wallet,
                receiveAddress,
                match(bigDecimalEq(BigDecimal.ONE)),
                forceRefresh = false,
            )
        }

        every { eip20Kit.balance } returns BigInteger("200000000")
        balanceProcessor.onNext(BigInteger("200000000"))

        verify(exactly = 1) {
            stackingManager.loadInvestmentData(
                wallet,
                receiveAddress,
                match(bigDecimalEq(BigDecimal("2"))),
                forceRefresh = false,
            )
        }

        every { eip20Kit.balance } returns BigInteger("50000000")
        balanceProcessor.onNext(BigInteger("50000000"))

        verify(exactly = 1) {
            stackingManager.loadInvestmentData(
                wallet,
                receiveAddress,
                match(bigDecimalEq(BigDecimal("0.5"))),
                forceRefresh = false,
            )
        }
    }

    @Test
    fun start_nonStakingWallet_doesNotLoadStakingData() = runTest {
        val balanceProcessor = PublishProcessor.create<BigInteger>()
        stubCommon(balanceProcessor)

        val adapter = createAdapter(UnconfinedTestDispatcher(testScheduler), nonStakingWallet)
        adapter.start()

        every { eip20Kit.balance } returns BigInteger("200000000")
        balanceProcessor.onNext(BigInteger("200000000"))

        verify(exactly = 0) {
            stackingManager.loadInvestmentData(any(), any(), any(), any())
        }
    }

    @Test
    fun stop_cancelsBalanceSubscription_subsequentEmissionsAreIgnored() = runTest {
        val balanceProcessor = PublishProcessor.create<BigInteger>()
        stubCommon(balanceProcessor)

        val adapter = createAdapter(UnconfinedTestDispatcher(testScheduler))
        adapter.start()
        adapter.stop()

        every { eip20Kit.balance } returns BigInteger("999999999")
        balanceProcessor.onNext(BigInteger("999999999"))

        // start() produced exactly one call; the post-stop emission must not add another.
        verify(exactly = 1) {
            stackingManager.loadInvestmentData(
                wallet,
                receiveAddress,
                any(),
                forceRefresh = false,
            )
        }
    }
}
