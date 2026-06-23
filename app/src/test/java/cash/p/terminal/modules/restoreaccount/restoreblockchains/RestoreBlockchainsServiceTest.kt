package cash.p.terminal.modules.restoreaccount.restoreblockchains

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.TokenAutoEnableManager
import cash.p.terminal.modules.enablecoin.blockchaintokens.BlockchainTokensService
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsService
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import cash.p.terminal.wallet.zcashAddressSpecTokenQueries
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.slot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.subjects.PublishSubject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import kotlin.test.assertEquals

class RestoreBlockchainsServiceTest : KoinTest {

    private val accountFactory = mockk<IAccountFactory>()
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>()
    private val tokenAutoEnableManager = mockk<TokenAutoEnableManager>(relaxed = true)
    private val blockchainTokensService = BlockchainTokensService()
    private val restoreSettingsService = mockk<RestoreSettingsService>(relaxed = true)
    private val walletFactory = mockk<WalletFactory>()
    private val getHardwarePublicKeyForWalletUseCase = mockk<GetHardwarePublicKeyForWalletUseCase>()

    private val approveSettingsSubject =
        PublishSubject.create<RestoreSettingsService.TokenWithSettings>()
    private val rejectSettingsSubject = PublishSubject.create<Token>()

    private val account = account()
    private val zcashTokens = zcashTokens()

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single { walletFactory }
                single { getHardwarePublicKeyForWalletUseCase }
            }
        )
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun enable_zcash_requestsBirthdayHeightWithoutTokenPicker() {
        val service = service()
        val zcashBlockchain = zcashTokens.first().blockchain
        val unifiedToken = zcashTokens.first {
            it.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified)
        }

        service.enable(zcashBlockchain)

        verify {
            restoreSettingsService.approveSettings(
                token = unifiedToken,
                forceRequest = true,
                initialConfig = null
            )
        }
        verify(exactly = 0) {
            restoreSettingsService.save(any(), any(), any())
        }
    }

    @Test
    fun restore_zcashAfterBirthdayApproval_savesHeightOnceAndCreatesFullGroup() {
        val service = service()
        val canRestoreObserver = service.canRestore.test()
        val unifiedToken = zcashTokens.first {
            it.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified)
        }
        val restoreSettings = RestoreSettings().apply { birthdayHeight = ZCASH_HEIGHT }
        val savedWalletsSlot = slot<List<Wallet>>()

        approveSettingsSubject.onNext(
            RestoreSettingsService.TokenWithSettings(unifiedToken, restoreSettings)
        )
        canRestoreObserver.awaitCount(2)

        service.restore()

        verify(exactly = 1) {
            restoreSettingsService.save(restoreSettings, account, BlockchainType.Zcash)
        }
        verify { walletManager.save(capture(savedWalletsSlot)) }
        assertEquals(
            zcashTokens.map { it.tokenQuery.id }.toSet(),
            savedWalletsSlot.captured.map { it.token.tokenQuery.id }.toSet()
        )
    }

    private fun service(): RestoreBlockchainsService {
        every { restoreSettingsService.approveSettingsObservable } returns approveSettingsSubject
        every { restoreSettingsService.rejectApproveSettingsObservable } returns rejectSettingsSubject
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            zcashTokens.filter { it.tokenQuery in firstArg<List<TokenQuery>>() }
        }
        every {
            accountFactory.account(
                name = "Account",
                type = account.type,
                origin = AccountOrigin.Restored,
                backedUp = true,
                fileBackedUp = false
            )
        } returns account
        zcashTokens.forEach { token ->
            every { walletFactory.create(token, account, null) } returns wallet(token)
            coEvery { getHardwarePublicKeyForWalletUseCase(account, token) } returns null
        }
        every { walletManager.save(any()) } just Runs

        return RestoreBlockchainsService(
            accountName = "Account",
            accountType = account.type,
            manualBackup = true,
            fileBackup = false,
            accountFactory = accountFactory,
            accountManager = accountManager,
            walletManager = walletManager,
            marketKit = marketKit,
            tokenAutoEnableManager = tokenAutoEnableManager,
            blockchainTokensService = blockchainTokensService,
            restoreSettingsService = restoreSettingsService
        )
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

    private fun wallet(token: Token): Wallet = mockk {
        every { this@mockk.token } returns token
        every { this@mockk.account } returns this@RestoreBlockchainsServiceTest.account
    }

    private companion object {
        const val ZCASH_HEIGHT = 2_000_000L
    }
}
