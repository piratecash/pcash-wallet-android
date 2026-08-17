package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.zcashMnemonicAccount
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EffectiveMonitoredChainsTest {

    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)

    private val effectiveMonitoredChains =
        EffectiveMonitoredChains(localStorage, accountManager, offlineModeManager)

    private val account = zcashMnemonicAccount("account-1")

    @Test
    fun chains_pausedChainForActiveAccount_excludesOnlyThatChain() {
        every { accountManager.activeAccount } returns account
        every { localStorage.pushEnabledBlockchainUids } returns setOf(
            BlockchainType.Bitcoin.uid,
            BlockchainType.Ethereum.uid,
        )
        every { offlineModeManager.isNetworkPaused(account.id, BlockchainType.Ethereum) } returns true
        every { offlineModeManager.isNetworkPaused(account.id, BlockchainType.Bitcoin) } returns false

        assertEquals(setOf(BlockchainType.Bitcoin), effectiveMonitoredChains.chains())
    }

    @Test
    fun chains_noActiveAccount_returnsEmptySet() {
        every { accountManager.activeAccount } returns null
        every { localStorage.pushEnabledBlockchainUids } returns setOf(BlockchainType.Bitcoin.uid)

        assertTrue(effectiveMonitoredChains.chains().isEmpty())
    }

    @Test
    fun chains_doesNotMutateSavedBlockchainUids() {
        every { accountManager.activeAccount } returns account
        every { localStorage.pushEnabledBlockchainUids } returns setOf(BlockchainType.Bitcoin.uid)

        effectiveMonitoredChains.chains()

        verify(exactly = 0) { localStorage.pushEnabledBlockchainUids = any() }
    }
}
