package cash.p.terminal.modules.balance

import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.zcashDisableTokenQueryIds
import cash.p.terminal.wallet.zcashMnemonicAccount
import cash.p.terminal.wallet.zcashTransparentWallet
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Pins the wiring that drops a chain's offline row once its wallet leaves the balance list. */
class BalanceActiveWalletRepositoryTest {

    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val userDeletedWalletManager = mockk<UserDeletedWalletManager>(relaxed = true)
    private val offlineModeUseCase = mockk<OfflineModeUseCase>(relaxed = true)
    private val evmSyncSourceManager = mockk<EvmSyncSourceManager>()

    private val account = zcashMnemonicAccount()

    private fun createRepository(): BalanceActiveWalletRepository {
        every { walletManager.activeWalletsFlow } returns MutableStateFlow(emptyList())
        every { evmSyncSourceManager.syncSourceObservable } returns Observable.never()
        return BalanceActiveWalletRepository(
            walletManager,
            userDeletedWalletManager,
            offlineModeUseCase,
            evmSyncSourceManager,
        )
    }

    @Test
    fun disable_regularWallet_resetsOfflineModeAfterDeletion() = runTest {
        val wallet = bitcoinWallet()

        createRepository().disable(wallet)

        coVerifyOrder {
            walletManager.deleteByWallet(wallet)
            offlineModeUseCase.resetIfBlockchainRemoved(account, BlockchainType.Bitcoin)
        }
    }

    @Test
    fun disable_zcashWallet_resetsOfflineModeAfterGroupedDeletion() = runTest {
        val wallet = zcashTransparentWallet(account)

        createRepository().disable(wallet)

        coVerifyOrder {
            walletManager.deleteByTokenQueryIds(account.id, zcashDisableTokenQueryIds)
            offlineModeUseCase.resetIfBlockchainRemoved(account, BlockchainType.Zcash)
        }
    }

    private fun bitcoinWallet(): Wallet = checkNotNull(
        WalletFactory(mockk(relaxed = true)).create(
            token = Token(
                coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
                blockchain = Blockchain(type = BlockchainType.Bitcoin, name = "Bitcoin", eip3091url = null),
                type = TokenType.Derived(TokenType.Derivation.Bip84),
                decimals = 8,
            ),
            account = account,
            hardwarePublicKey = null,
        )
    )
}
