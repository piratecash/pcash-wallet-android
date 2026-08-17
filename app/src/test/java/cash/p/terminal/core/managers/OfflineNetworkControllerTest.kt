package cash.p.terminal.core.managers

import cash.p.terminal.core.adapters.BitcoinBaseAdapter
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.zcashMnemonicAccount
import io.horizontalsystems.bitcoincore.AbstractKit
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OfflineNetworkController] is the family table: per blockchain family it must call exactly one
 * kit-level pause/resume and read back the matching authoritative "is offline" state. Kit-manager
 * internals themselves are covered by the existing *KitManagerOfflineGateTest suites.
 */
class OfflineNetworkControllerTest {

    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val evmBlockchainManager = mockk<EvmBlockchainManager>(relaxed = true)
    private val solanaKitManager = mockk<SolanaKitManager>(relaxed = true)
    private val tronKitManager = mockk<TronKitManager>(relaxed = true)
    private val tonKitManager = mockk<TonKitManager>(relaxed = true)
    private val stellarKitManager = mockk<StellarKitManager>(relaxed = true)
    private val moneroKitManager = mockk<MoneroKitManager>(relaxed = true)

    private val controller = OfflineNetworkController(
        adapterManager,
        evmBlockchainManager,
        solanaKitManager,
        tronKitManager,
        tonKitManager,
        stellarKitManager,
        moneroKitManager,
    )

    private val account = zcashMnemonicAccount(ACCOUNT_ID)

    private fun wallet(blockchainType: BlockchainType, tokenType: TokenType = TokenType.Native): Wallet = checkNotNull(
        WalletFactory(mockk(relaxed = true)).create(
            Token(
                coin = Coin(uid = blockchainType.uid, name = blockchainType.uid, code = blockchainType.uid),
                blockchain = Blockchain(type = blockchainType, name = blockchainType.uid, eip3091url = null),
                type = tokenType,
                decimals = 8,
            ),
            account,
            null,
        )
    )

    private fun adapterFor(member: Wallet): IAdapter = mockk<IAdapter>(relaxed = true).also {
        every { adapterManager.getAdapterForWalletOld(member) } returns it
    }

    private fun setOnline(currentAccount: () -> Any?, networkStarted: () -> Boolean?) {
        every { currentAccount() } returns account
        every { networkStarted() } returns true
    }

    private fun setOnline(kitManager: EvmKitManager) {
        val wrapper = mockk<EvmKitWrapper>(relaxed = true)
        val kit = mockk<EthereumKit>(relaxed = true)
        every { kitManager.currentAccount } returns account
        every { kitManager.evmKitWrapper } returns wrapper
        every { wrapper.evmKit } returns kit
        every { kit.isStarted } returns true
    }

    @Test
    fun pause_evmMember_pausesAdapterAndDelegatesToEvmKitManager() = runTest {
        val member = wallet(BlockchainType.Base) // exercises the EVM_BLOCKCHAIN_TYPES set, not just Ethereum
        val adapter = adapterFor(member)
        val evmKitManager = mockk<EvmKitManager>(relaxed = true)
        every { evmBlockchainManager.getEvmKitManager(BlockchainType.Base) } returns evmKitManager
        setOnline(evmKitManager)

        controller.pause(member)

        coVerify(exactly = 1) { adapter.pauseNetwork() }
        coVerify(exactly = 1) { evmKitManager.pauseNetwork(account) }
    }

    @Test
    fun resume_evmMember_resumesAdapterAndDelegatesToEvmKitManager() = runTest {
        val member = wallet(BlockchainType.Ethereum)
        val adapter = adapterFor(member)
        val evmKitManager = mockk<EvmKitManager>(relaxed = true)
        every { evmBlockchainManager.getEvmKitManager(BlockchainType.Ethereum) } returns evmKitManager

        controller.resume(member)

        coVerify(exactly = 1) { adapter.resumeNetwork() }
        coVerify(exactly = 1) { evmKitManager.resumeNetwork(account) }
    }

    @Test
    fun pause_sharedEvmMembers_pausesEachAdapterAndKitOnce() = runTest {
        val native = wallet(BlockchainType.Ethereum)
        val token = wallet(BlockchainType.Ethereum, TokenType.Eip20("0xUSDC"))
        val nativeAdapter = adapterFor(native)
        val tokenAdapter = adapterFor(token)
        var started = true
        val evmKitManager = mockk<EvmKitManager>(relaxed = true)
        val evmKitWrapper = mockk<EvmKitWrapper>(relaxed = true)
        val evmKit = mockk<EthereumKit>(relaxed = true)
        every { evmBlockchainManager.getEvmKitManager(BlockchainType.Ethereum) } returns evmKitManager
        every { evmKitManager.currentAccount } returns account
        every { evmKitManager.evmKitWrapper } returns evmKitWrapper
        every { evmKitWrapper.evmKit } returns evmKit
        every { evmKit.isStarted } answers { started }
        coEvery { evmKitManager.pauseNetwork(account) } coAnswers { started = false }
        coEvery { evmKitManager.resumeNetwork(account) } coAnswers { started = true }

        controller.pause(native)
        controller.pause(token)
        controller.resume(native)
        controller.resume(token)

        coVerify(exactly = 1) { nativeAdapter.pauseNetwork() }
        coVerify(exactly = 1) { tokenAdapter.pauseNetwork() }
        coVerify(exactly = 1) { nativeAdapter.resumeNetwork() }
        coVerify(exactly = 1) { tokenAdapter.resumeNetwork() }
        coVerify(exactly = 1) { evmKitManager.pauseNetwork(account) }
        coVerify(exactly = 1) { evmKitManager.resumeNetwork(account) }
    }

    @Test
    fun pause_solanaMember_delegatesToSolanaKitManager() = runTest {
        val member = wallet(BlockchainType.Solana)
        adapterFor(member)
        setOnline({ solanaKitManager.currentAccount }, { solanaKitManager.solanaKitWrapper?.networkStarted })

        controller.pause(member)

        coVerify(exactly = 1) { solanaKitManager.pauseNetwork(account) }
    }

    @Test
    fun pause_tronMember_delegatesToTronKitManager() = runTest {
        val member = wallet(BlockchainType.Tron)
        adapterFor(member)
        setOnline({ tronKitManager.currentAccount }, { tronKitManager.tronKitWrapper?.networkStarted })

        controller.pause(member)

        coVerify(exactly = 1) { tronKitManager.pauseNetwork(account) }
    }

    @Test
    fun pause_tonMember_delegatesToTonKitManager() = runTest {
        val member = wallet(BlockchainType.Ton)
        adapterFor(member)
        setOnline({ tonKitManager.currentAccount }, { tonKitManager.tonKitWrapper?.networkStarted })

        controller.pause(member)

        coVerify(exactly = 1) { tonKitManager.pauseNetwork(account) }
    }

    @Test
    fun pause_stellarMember_delegatesToStellarKitManager() = runTest {
        val member = wallet(BlockchainType.Stellar)
        adapterFor(member)
        setOnline({ stellarKitManager.currentAccount }, { stellarKitManager.stellarKitWrapper?.networkStarted })

        controller.pause(member)

        coVerify(exactly = 1) { stellarKitManager.pauseNetwork(account) }
    }

    @Test
    fun pause_moneroMember_delegatesToMoneroKitManager() = runTest {
        val member = wallet(BlockchainType.Monero)
        adapterFor(member)
        setOnline({ moneroKitManager.currentAccount }, { moneroKitManager.moneroKitWrapper?.isNetworkOnline })

        controller.pause(member)

        coVerify(exactly = 1) { moneroKitManager.pauseNetwork(account) }
    }

    @Test
    fun pause_bitcoinLikeMember_onlyPausesAdapterNoKitManagerCall() = runTest {
        val member = wallet(BlockchainType.Bitcoin)
        val adapter = adapterFor(member)

        controller.pause(member)

        coVerify(exactly = 1) { adapter.pauseNetwork() }
        coVerify(exactly = 0) { solanaKitManager.pauseNetwork(any()) }
        coVerify(exactly = 0) { tronKitManager.pauseNetwork(any()) }
        coVerify(exactly = 0) { moneroKitManager.pauseNetwork(any()) }
    }

    @Test
    fun isOffline_evmDifferentAccount_returnsTrue() {
        val member = wallet(BlockchainType.Ethereum)
        val evmKitManager = mockk<EvmKitManager>(relaxed = true)
        every { evmBlockchainManager.getEvmKitManager(BlockchainType.Ethereum) } returns evmKitManager
        every { evmKitManager.currentAccount } returns null

        assertTrue(controller.isOffline(member))
    }

    @Test
    fun isOffline_evmStartedForCurrentAccount_returnsFalse() {
        val member = wallet(BlockchainType.Ethereum)
        val evmKitManager = mockk<EvmKitManager>(relaxed = true)
        val evmKitWrapper = mockk<EvmKitWrapper>(relaxed = true)
        val evmKit = mockk<EthereumKit>(relaxed = true)
        every { evmBlockchainManager.getEvmKitManager(BlockchainType.Ethereum) } returns evmKitManager
        every { evmKitManager.currentAccount } returns account
        every { evmKitManager.evmKitWrapper } returns evmKitWrapper
        every { evmKitWrapper.evmKit } returns evmKit
        every { evmKit.isStarted } returns true

        assertFalse(controller.isOffline(member))
    }

    @Test
    fun isOffline_evmNotStartedForCurrentAccount_returnsTrue() {
        val member = wallet(BlockchainType.Ethereum)
        val evmKitManager = mockk<EvmKitManager>(relaxed = true)
        val evmKitWrapper = mockk<EvmKitWrapper>(relaxed = true)
        val evmKit = mockk<EthereumKit>(relaxed = true)
        every { evmBlockchainManager.getEvmKitManager(BlockchainType.Ethereum) } returns evmKitManager
        every { evmKitManager.currentAccount } returns account
        every { evmKitManager.evmKitWrapper } returns evmKitWrapper
        every { evmKitWrapper.evmKit } returns evmKit
        every { evmKit.isStarted } returns false

        assertTrue(controller.isOffline(member))
    }

    @Test
    fun isOffline_zcashSynchronizerNotRunning_returnsTrue() {
        val member = wallet(BlockchainType.Zcash)
        val adapter = mockk<ZcashAdapter>(relaxed = true)
        every { adapterManager.getAdapterForWalletOld(member) } returns adapter
        every { adapter.isNetworkPaused } returns true

        assertTrue(controller.isOffline(member))
    }

    @Test
    fun isOffline_zcashSynchronizerRunning_returnsFalse() {
        val member = wallet(BlockchainType.Zcash)
        val adapter = mockk<ZcashAdapter>(relaxed = true)
        every { adapterManager.getAdapterForWalletOld(member) } returns adapter
        every { adapter.isNetworkPaused } returns false

        assertFalse(controller.isOffline(member))
    }

    // A sync error is not a pause: an erroring but online synchronizer must still be paused on demand.
    @Test
    fun isOffline_zcashRunningWithSyncError_returnsFalse() {
        val member = wallet(BlockchainType.Zcash)
        val adapter = mockk<ZcashAdapter>(relaxed = true)
        every { adapterManager.getAdapterForWalletOld(member) } returns adapter
        every { adapter.isNetworkPaused } returns false
        every { adapter.balanceState } returns AdapterState.NotSynced(RuntimeException("test"))

        assertFalse(controller.isOffline(member))
    }

    @Test
    fun isOffline_zcashNoAdapter_returnsTrue() {
        val member = wallet(BlockchainType.Zcash)
        every { adapterManager.getAdapterForWalletOld(member) } returns null

        assertTrue(controller.isOffline(member))
    }

    @Test
    fun isOffline_bitcoinLikeNetworkPaused_returnsTrue() {
        val member = wallet(BlockchainType.Bitcoin)
        val adapter = mockk<BitcoinBaseAdapter>(relaxed = true)
        val kit = mockk<AbstractKit>(relaxed = true)
        every { adapterManager.getAdapterForWalletOld(member) } returns adapter
        every { adapter.kit } returns kit
        every { kit.isNetworkPaused } returns true

        assertTrue(controller.isOffline(member))
    }

    @Test
    fun isOffline_bitcoinLikeNetworkRunning_returnsFalse() {
        val member = wallet(BlockchainType.Bitcoin)
        val adapter = mockk<BitcoinBaseAdapter>(relaxed = true)
        val kit = mockk<AbstractKit>(relaxed = true)
        every { adapterManager.getAdapterForWalletOld(member) } returns adapter
        every { adapter.kit } returns kit
        every { kit.isNetworkPaused } returns false

        assertFalse(controller.isOffline(member))
    }

    private companion object {
        const val ACCOUNT_ID = "network-controller-account"
    }
}
