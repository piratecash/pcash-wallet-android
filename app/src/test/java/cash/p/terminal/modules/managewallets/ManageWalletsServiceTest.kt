package cash.p.terminal.modules.managewallets

import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsService
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManageWalletsServiceTest {

    private val dispatcher = StandardTestDispatcher()
    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val restoreSettingsService = mockk<RestoreSettingsService>(relaxed = true)
    private val userDeletedWalletManager = mockk<UserDeletedWalletManager>(relaxed = true)

    private val approveSettingsSubject =
        PublishSubject.create<RestoreSettingsService.TokenWithSettings>()
    private val rejectSettingsSubject = PublishSubject.create<Token>()
    private val activeWalletsFlow = MutableStateFlow<List<Wallet>>(emptyList())

    private val account = account()
    private val zcashTokens = zcashTokens()
    private var activeWallets = emptyList<Wallet>()
    private lateinit var service: ManageWalletsService

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        activeWallets = emptyList()
        activeWalletsFlow.value = activeWallets

        every { restoreSettingsService.approveSettingsObservable } returns approveSettingsSubject
        every { restoreSettingsService.rejectApproveSettingsObservable } returns rejectSettingsSubject
        every { walletManager.activeWallets } answers { activeWallets }
        every { walletManager.activeWalletsFlow } returns activeWalletsFlow

        service = ManageWalletsService(
            walletManager = walletManager,
            restoreSettingsService = restoreSettingsService,
            fullCoinsProvider = null,
            account = account,
            userDeletedWalletManager = userDeletedWalletManager
        )
    }

    @After
    fun tearDown() {
        service.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun enable_zcashRestoredWithoutActiveZcash_requestsBirthdayHeight() = runTest(dispatcher) {
        val unifiedToken = zcashTokens.first {
            it.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified)
        }

        service.enable(unifiedToken)
        advanceUntilIdle()

        verify {
            restoreSettingsService.approveSettings(
                token = unifiedToken,
                account = account,
                forceRequest = true,
                initialConfig = null
            )
        }
    }

    private fun zcashTokens(): List<Token> {
        val coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC")
        val blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null)
        return TokenType.AddressSpecType.entries.map {
            Token(
                coin = coin,
                blockchain = blockchain,
                type = TokenType.AddressSpecTyped(it),
                decimals = 8
            )
        }
    }

    private fun account() = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(List(12) { "word" }, ""),
        origin = AccountOrigin.Restored,
        level = 0
    )

}
