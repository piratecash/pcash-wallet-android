package cash.p.terminal.wallet.useCases

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IDeletedWalletRestorer
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.zcashAddressSpecTokenQueries
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletUseCaseTest {

    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val getHardwarePublicKeyForWalletUseCase = mockk<GetHardwarePublicKeyForWalletUseCase>()
    private val scanToAddUseCase = mockk<ScanToAddUseCase>()
    private val walletFactory = mockk<WalletFactory>()
    private val marketKit = mockk<MarketKitWrapper>()
    private val deletedWalletRestorer = mockk<IDeletedWalletRestorer>(relaxed = true)

    private lateinit var useCase: WalletUseCase

    private lateinit var activeWalletsFlow: MutableStateFlow<List<Wallet>>
    private var activeWallets: List<Wallet> = emptyList()

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        activeWallets = emptyList()
        activeWalletsFlow = MutableStateFlow(activeWallets)

        every { walletManager.activeWallets } answers { activeWallets }
        every { walletManager.activeWalletsFlow } returns activeWalletsFlow
        coEvery { walletManager.save(any()) } answers {
            val wallets = firstArg<List<Wallet>>()
            activeWallets = wallets
            activeWalletsFlow.value = wallets
        }
        coEvery { walletManager.saveSuspended(any()) } answers {
            val wallets = firstArg<List<Wallet>>()
            activeWallets = wallets
            activeWalletsFlow.value = wallets
        }

        coEvery { scanToAddUseCase.addTokensByScan(any(), any(), any()) } returns true

        useCase = WalletUseCase(
            walletManager,
            accountManager,
            adapterManager,
            getHardwarePublicKeyForWalletUseCase,
            scanToAddUseCase,
            walletFactory,
            marketKit,
            deletedWalletRestorer
        )
    }

    @AfterTest
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `createWallets returns false when no active account`() = runTest {
        every { accountManager.activeAccount } returns null

        val token = token("USDT", BlockchainType.Ethereum)

        val result = useCase.createWallets(setOf(token))

        assertFalse(result)
        coVerify(exactly = 0) { walletManager.save(any()) }
        coVerify(exactly = 0) { walletManager.saveSuspended(any()) }
    }

    @Test
    fun `createWallets saves wallets for software account`() = runTest {
        val account = softwareAccount()
        every { accountManager.activeAccount } returns account

        val token = token("ETH", BlockchainType.Ethereum)
        val wallet = wallet(token, account)

        every { walletFactory.create(token, account, null) } returns wallet

        val result = useCase.createWallets(setOf(token))

        assertTrue(result)
        coVerify { walletManager.saveSuspended(match { it.single() == wallet }) }
    }

    @Test
    fun createWallets_zcashAddressSpec_savesFullGroup() = runTest {
        val account = softwareAccount()
        every { accountManager.activeAccount } returns account

        val zcashTokens = zcashTokens()
        val unifiedToken = zcashTokens.first { it.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified) }
        val zcashWallets = zcashTokens.map { wallet(it, account) }

        every { marketKit.tokens(zcashAddressSpecTokenQueries) } returns zcashTokens
        zcashTokens.forEachIndexed { index, token ->
            every { walletFactory.create(token, account, null) } returns zcashWallets[index]
        }

        val result = useCase.createWallets(setOf(unifiedToken))

        assertTrue(result)
        coVerify {
            walletManager.saveSuspended(match { wallets ->
                wallets.map { it.token.tokenQuery.id }.toSet() ==
                    zcashTokens.map { it.tokenQuery.id }.toSet()
            })
        }
    }

    @Test
    fun createWallets_zcashAddressSpec_marketKitMissingGroup_preservesRequestedToken() = runTest {
        val account = softwareAccount()
        every { accountManager.activeAccount } returns account

        val unifiedToken = zcashTokens()
            .first { it.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified) }
        val unifiedWallet = wallet(unifiedToken, account)

        every { marketKit.tokens(zcashAddressSpecTokenQueries) } returns emptyList()
        every { walletFactory.create(unifiedToken, account, null) } returns unifiedWallet

        val result = useCase.createWallets(setOf(unifiedToken))

        assertTrue(result)
        coVerify { walletManager.saveSuspended(match { it.single() == unifiedWallet }) }
    }

    @Test
    fun `createWallets for hardware account skips scan when keys present`() = runTest {
        val account = hardwareAccount()
        every { accountManager.activeAccount } returns account

        val token = token("USDT", BlockchainType.Ethereum)
        val hardwareKey = hardwareKey(account, token)
        val wallet = wallet(token, account, hardwareKey)

        coEvery {
            getHardwarePublicKeyForWalletUseCase(account, token.blockchainType, token.type)
        } returnsMany listOf(hardwareKey, hardwareKey)
        every { walletFactory.create(token, account, hardwareKey) } returns wallet

        val result = useCase.createWallets(setOf(token))

        assertTrue(result)
        coVerify(exactly = 0) { scanToAddUseCase.addTokensByScan(any(), any(), any()) }
        coVerify { walletManager.save(match { it.single() == wallet }) }
    }

    @Test
    fun `createWallets for hardware account scans when keys missing`() = runTest {
        val account = hardwareAccount()
        every { accountManager.activeAccount } returns account

        val tokenA = token("USDT", BlockchainType.Ethereum)
        val tokenB = token("BTC", BlockchainType.BinanceSmartChain)
        val keyA = hardwareKey(account, tokenA)
        val keyB = hardwareKey(account, tokenB)
        val walletA = wallet(tokenA, account, keyA)
        val walletB = wallet(tokenB, account, keyB)

        coEvery {
            getHardwarePublicKeyForWalletUseCase(account, tokenA.blockchainType, tokenA.type)
        } returnsMany listOf(keyA, keyA)
        coEvery {
            getHardwarePublicKeyForWalletUseCase(account, tokenB.blockchainType, tokenB.type)
        } returnsMany listOf(null, keyB)
        coEvery {
            scanToAddUseCase.addTokensByScan(
                match { it.size == 2 },
                accountCard(account).cardId,
                account.id
            )
        } returns true
        every { walletFactory.create(tokenA, account, keyA) } returns walletA
        every { walletFactory.create(tokenB, account, keyB) } returns walletB

        val result = useCase.createWallets(setOf(tokenA, tokenB))

        assertTrue(result)
        coVerify {
            walletManager.save(match { it.toSet() == setOf(walletA, walletB) })
        }
        coVerify(exactly = 1) {
            scanToAddUseCase.addTokensByScan(any(), accountCard(account).cardId, account.id)
        }
    }

    @Test
    fun `createWallets returns false when scan fails`() = runTest {
        val account = hardwareAccount()
        every { accountManager.activeAccount } returns account

        val tokenA = token("USDT", BlockchainType.Ethereum)
        val tokenB = token("BTC", BlockchainType.BinanceSmartChain)
        val keyA = hardwareKey(account, tokenA)
        val walletA = wallet(tokenA, account, keyA)

        coEvery {
            getHardwarePublicKeyForWalletUseCase(account, tokenA.blockchainType, tokenA.type)
        } returnsMany listOf(keyA, keyA)
        coEvery {
            getHardwarePublicKeyForWalletUseCase(account, tokenB.blockchainType, tokenB.type)
        } returnsMany listOf(null, null)
        coEvery {
            scanToAddUseCase.addTokensByScan(any(), accountCard(account).cardId, account.id)
        } returns false
        every { walletFactory.create(tokenA, account, keyA) } returns walletA

        val result = useCase.createWallets(setOf(tokenA, tokenB))

        assertFalse(result)
        coVerify {
            walletManager.save(match { it == listOf(walletA) })
        }
    }

    @Test
    fun `createWalletIfNotExists returns existing wallet`() = runTest {
        val account = softwareAccount()
        every { accountManager.activeAccount } returns account

        val token = token("ETH", BlockchainType.Ethereum)
        val wallet = wallet(token, account)
        activeWallets = listOf(wallet)
        activeWalletsFlow.value = activeWallets

        val result = useCase.createWalletIfNotExists(token)

        assertSame(wallet, result)
        coVerify(exactly = 0) { walletManager.saveSuspended(any()) }
    }

    @Test
    fun `createWalletIfNotExists creates new wallet`() = runTest {
        val account = softwareAccount()
        every { accountManager.activeAccount } returns account

        val token = token("ETH", BlockchainType.Ethereum)
        val wallet = wallet(token, account)

        activeWallets = emptyList()
        activeWalletsFlow.value = activeWallets

        every { walletFactory.create(token, account, null) } returns wallet

        val result = useCase.createWalletIfNotExists(token)

        assertSame(wallet, result)
        coVerify { walletManager.saveSuspended(match { it.single() == wallet }) }
    }

    @Test
    fun createWalletIfNotExists_zcashAddressSpec_returnsRequestedWalletAndSavesGroup() = runTest {
        val account = softwareAccount()
        every { accountManager.activeAccount } returns account

        val zcashTokens = zcashTokens()
        val transparentToken = zcashTokens.first {
            it.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent)
        }
        val zcashWallets = zcashTokens.associateWith { wallet(it, account) }

        activeWallets = emptyList()
        activeWalletsFlow.value = activeWallets

        every { marketKit.tokens(zcashAddressSpecTokenQueries) } returns zcashTokens
        zcashWallets.forEach { (token, wallet) ->
            every { walletFactory.create(token, account, null) } returns wallet
        }

        val result = useCase.createWalletIfNotExists(transparentToken)

        assertSame(zcashWallets.getValue(transparentToken), result)
        coVerify {
            walletManager.saveSuspended(match { wallets ->
                wallets.map { it.token.tokenQuery.id }.toSet() ==
                    zcashTokens.map { it.tokenQuery.id }.toSet()
            })
        }
    }

    @Test
    fun `awaitWallets returns immediately when wallets exist`() = runTest {
        val account = softwareAccount()
        val token = token("ETH", BlockchainType.Ethereum)
        val wallet = wallet(token, account)

        activeWallets = listOf(wallet)
        activeWalletsFlow.value = activeWallets

        useCase.awaitWallets(setOf(token))
    }

    @Test
    fun `awaitWallets suspends until wallets available`() = runTest {
        val account = softwareAccount()
        val token = token("ETH", BlockchainType.Ethereum)
        val wallet = wallet(token, account)

        activeWallets = emptyList()
        activeWalletsFlow.value = activeWallets

        val deferred = async { useCase.awaitWallets(setOf(token)) }

        advanceUntilIdle()
        assertFalse(deferred.isCompleted)

        activeWallets = listOf(wallet)
        activeWalletsFlow.value = activeWallets

        advanceUntilIdle()
        deferred.await()
    }

    @Test
    fun awaitWallets_zcashAddressSpec_waitsForFullGroup() = runTest {
        val account = softwareAccount()
        val zcashTokens = zcashTokens()
        val unifiedToken = zcashTokens.first { it.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified) }
        val zcashWallets = zcashTokens.map { wallet(it, account) }

        every { marketKit.tokens(zcashAddressSpecTokenQueries) } returns zcashTokens

        activeWallets = emptyList()
        activeWalletsFlow.value = activeWallets

        val deferred = async { useCase.awaitWallets(setOf(unifiedToken)) }

        advanceUntilIdle()
        assertFalse(deferred.isCompleted)

        activeWallets = listOf(zcashWallets.first { it.token == unifiedToken })
        activeWalletsFlow.value = activeWallets

        advanceUntilIdle()
        assertFalse(deferred.isCompleted)

        activeWallets = zcashWallets
        activeWalletsFlow.value = activeWallets

        advanceUntilIdle()
        deferred.await()
    }

    @Test
    fun `getReceiveAddress delegates to adapter`() {
        val account = softwareAccount()
        val token = token("ETH", BlockchainType.Ethereum)
        val wallet = wallet(token, account)
        val adapter = mockk<IReceiveAdapter>()

        activeWallets = listOf(wallet)
        activeWalletsFlow.value = activeWallets
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        every { adapter.receiveAddress } returns "0x123"

        val address = useCase.getReceiveAddress(token)

        assertEquals("0x123", address)
    }

    private fun token(
        symbol: String,
        blockchainType: BlockchainType,
        type: TokenType = TokenType.Native,
        decimals: Int = 18
    ): Token {
        val coin = Coin(
            uid = "${blockchainType.uid}-$symbol",
            name = symbol,
            code = symbol
        )
        val blockchain = Blockchain(blockchainType, blockchainType.uid, null)
        return Token(coin, blockchain, type, decimals)
    }

    private fun softwareAccount(id: String = UUID.randomUUID().toString()) = Account(
        id = id,
        name = "Account-$id",
        type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
        origin = AccountOrigin.Created,
        level = 0
    )

    private fun hardwareAccount(id: String = UUID.randomUUID().toString()) = Account(
        id = id,
        name = "Hardware-$id",
        type = AccountType.HardwareCard(
            cardId = "card-$id",
            backupCardsCount = 0,
            walletPublicKey = "pk",
            signedHashes = 0
        ),
        origin = AccountOrigin.Created,
        level = 0
    )

    private fun hardwareKey(account: Account, token: Token) = HardwarePublicKey(
        accountId = account.id,
        blockchainType = token.blockchainType.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = token.type,
        key = SecretString("key"),
        derivationPath = "m/44/0/0",
        publicKey = byteArrayOf(1, 2, 3),
        derivedPublicKey = byteArrayOf(4, 5, 6)
    )

    private fun wallet(token: Token, account: Account, hardwareKey: HardwarePublicKey? = null) =
        Wallet(token, account, hardwareKey)

    private fun accountCard(account: Account) = account.type as AccountType.HardwareCard

    private fun zcashTokens(): List<Token> {
        val blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null)
        val coin = Coin(
            uid = "zcash",
            name = "Zcash",
            code = "ZEC"
        )

        return TokenType.AddressSpecType.entries.map {
            Token(
                coin = coin,
                blockchain = blockchain,
                type = TokenType.AddressSpecTyped(it),
                decimals = 8
            )
        }
    }
}
