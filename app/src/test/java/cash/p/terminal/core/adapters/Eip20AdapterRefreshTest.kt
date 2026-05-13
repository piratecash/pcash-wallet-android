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
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class Eip20AdapterRefreshTest {

    private val context = mockk<Context>(relaxed = true)
    private val evmTransactionRepository = mockk<EvmTransactionRepository>()
    private val coinManager = mockk<ICoinManager>(relaxed = true)
    private val evmLabelManager = mockk<EvmLabelManager>(relaxed = true)
    private val stackingManager = mockk<StackingManager>(relaxed = true)
    private val eip20Kit = mockk<Erc20Kit>(relaxed = true)
    private val repositoryReceiveAddress = mockk<Address>()

    private val stakingWallet: Wallet = WalletFactory.previewStakingWallet()
    private val nonStakingWallet: Wallet = WalletFactory.previewWallet()
    private val receiveAddress = "0x0000000000000000000000000000000000000001"
    private val tokenBalance = BigDecimal.ONE

    private fun stubCommon() {
        every { evmTransactionRepository.transactionSyncSourceStorage } returns mockk<TransactionSyncSourceStorage>(relaxed = true)
        every { evmTransactionRepository.buildErc20Kit(context, any()) } returns eip20Kit
        every { repositoryReceiveAddress.eip55 } returns receiveAddress
        every { evmTransactionRepository.receiveAddress } returns repositoryReceiveAddress
        every { stackingManager.unpaidFlow(any()) } returns MutableStateFlow(BigDecimal.ZERO)
        every { eip20Kit.balance } returns BigInteger("100000000")
    }

    private fun createAdapter(wallet: Wallet) = Eip20Adapter(
        context = context,
        evmTransactionRepository = evmTransactionRepository,
        contractAddress = receiveAddress,
        baseToken = wallet.token,
        coinManager = coinManager,
        wallet = wallet,
        evmLabelManager = evmLabelManager,
        stackingManager = stackingManager,
    )

    @Test
    fun refresh_stakingWallet_refreshesKitBeforeReloadingStakingData() = runTest {
        stubCommon()
        val adapter = createAdapter(stakingWallet)

        adapter.refresh()

        verifyOrder {
            eip20Kit.refresh()
            stackingManager.loadInvestmentData(stakingWallet, receiveAddress, tokenBalance, forceRefresh = true)
        }
    }

    @Test
    fun refresh_nonStakingWallet_refreshesKitButSkipsStakingReload() = runTest {
        stubCommon()
        val adapter = createAdapter(nonStakingWallet)

        adapter.refresh()

        verify(exactly = 1) { eip20Kit.refresh() }
        verify(exactly = 0) { stackingManager.loadInvestmentData(any(), any(), any(), any()) }
    }
}
