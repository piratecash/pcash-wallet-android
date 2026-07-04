package cash.p.terminal.modules.enablecoin.restoresettings

import cash.p.terminal.core.managers.LitecoinBirthdayProvider
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import kotlin.test.assertEquals

class RestoreSettingsServiceTest : KoinTest {

    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val manager = mockk<RestoreSettingsManager>(relaxed = true)
    private val zcashBirthdayProvider = mockk<ZcashBirthdayProvider>(relaxed = true)
    private val litecoinBirthdayProvider = mockk<LitecoinBirthdayProvider>()

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<IAccountManager> { accountManager }
            }
        )
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun enter_litecoinMwebRestoreAsNewWithoutHeight_usesLatestLitecoinCheckpoint() {
        every { accountManager.activeAccount } returns null
        every { litecoinBirthdayProvider.getLatestCheckpointBlockHeight() } returns LITECOIN_CHECKPOINT
        val service = service()
        val observer = service.approveSettingsObservable.test()

        service.enter(TokenConfig(birthdayHeight = null, restoreAsNew = true), litecoinMwebToken())

        assertEquals(LITECOIN_CHECKPOINT, observer.values().single().settings.birthdayHeight)
    }

    @Test
    fun enter_litecoinMwebRestoreFromHeight_usesConfiguredHeight() {
        every { accountManager.activeAccount } returns null
        val service = service()
        val observer = service.approveSettingsObservable.test()

        service.enter(TokenConfig(birthdayHeight = "100", restoreAsNew = false), litecoinMwebToken())

        assertEquals(100L, observer.values().single().settings.birthdayHeight)
    }

    @Test
    fun enter_litecoinMwebRestoreFromLargeHeight_usesConfiguredHeight() {
        every { accountManager.activeAccount } returns null
        val service = service()
        val observer = service.approveSettingsObservable.test()

        service.enter(TokenConfig(birthdayHeight = "100000000", restoreAsNew = false), litecoinMwebToken())

        assertEquals(100_000_000L, observer.values().single().settings.birthdayHeight)
    }

    @Test
    fun enter_litecoinMwebRestoreFromHeightAboveIntRange_ignoresConfiguredHeight() {
        every { accountManager.activeAccount } returns null
        val service = service()
        val observer = service.approveSettingsObservable.test()

        service.enter(
            TokenConfig(
                birthdayHeight = (Int.MAX_VALUE.toLong() + 1).toString(),
                restoreAsNew = false
            ),
            litecoinMwebToken()
        )

        assertEquals(null, observer.values().single().settings.birthdayHeight)
    }

    @Test
    fun approveSettings_litecoinMwebWithExistingHeight_emitsExistingSettings() {
        val account = account()
        val settings = RestoreSettings().apply {
            birthdayHeight = 2_257_920
        }
        every { manager.settings(account, BlockchainType.Litecoin) } returns settings
        val service = service()
        val approveObserver = service.approveSettingsObservable.test()
        val requestObserver = service.requestObservable.test()

        service.approveSettings(litecoinMwebToken(), account)

        assertEquals(2_257_920L, approveObserver.values().single().settings.birthdayHeight)
        assertEquals(0, requestObserver.valueCount())
    }

    @Test
    fun approveSettings_litecoinMwebForceRequest_includesAccountIdInRequest() {
        val account = account()
        every { manager.settings(account, BlockchainType.Litecoin) } returns RestoreSettings()
        val service = service()
        val requestObserver = service.requestObservable.test()

        service.approveSettings(litecoinMwebToken(), account, forceRequest = true)

        assertEquals(account.id, requestObserver.values().single().accountId)
    }

    @Test
    fun approveSettings_zcashRestoredWithoutHeight_requestsBirthdayHeight() {
        val account = account()
        every { manager.settings(account, BlockchainType.Zcash) } returns RestoreSettings()
        val service = service()
        val approveObserver = service.approveSettingsObservable.test()
        val requestObserver = service.requestObservable.test()

        service.approveSettings(zcashToken(), account)

        assertEquals(0, approveObserver.valueCount())
        with(requestObserver.values().single()) {
            assertEquals(zcashToken(), token)
            assertEquals(account.id, accountId)
            assertEquals(RestoreSettingsService.RequestType.BirthdayHeight, requestType)
        }
    }

    @Test
    fun approveSettings_zcashCreatedAccount_usesLatestCheckpoint() {
        val account = account(origin = AccountOrigin.Created)
        every {
            manager.getSettingValueForCreatedAccount(
                RestoreSettingType.BirthdayHeight,
                BlockchainType.Zcash
            )
        } returns ZCASH_CHECKPOINT.toString()
        val service = service()
        val approveObserver = service.approveSettingsObservable.test()
        val requestObserver = service.requestObservable.test()

        service.approveSettings(zcashToken(), account)

        assertEquals(ZCASH_CHECKPOINT, approveObserver.values().single().settings.birthdayHeight)
        assertEquals(0, requestObserver.valueCount())
    }

    private fun service() = RestoreSettingsService(
        manager = manager,
        zcashBirthdayProvider = zcashBirthdayProvider,
        litecoinBirthdayProvider = litecoinBirthdayProvider
    )

    private fun litecoinMwebToken() = Token(
        coin = Coin("litecoin", "Litecoin", "LTC"),
        blockchain = Blockchain(BlockchainType.Litecoin, "Litecoin", null),
        type = TokenType.Mweb,
        decimals = 8
    )

    private fun zcashToken() = Token(
        coin = Coin("zcash", "Zcash", "ZEC"),
        blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null),
        type = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified),
        decimals = 8
    )

    private fun account(origin: AccountOrigin = AccountOrigin.Restored) = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(List(12) { "word" }, ""),
        origin = origin,
        level = 0
    )

    private companion object {
        const val LITECOIN_CHECKPOINT = 3_000_000L
        const val ZCASH_CHECKPOINT = 2_000_000L
    }
}
