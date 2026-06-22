package cash.p.terminal.modules.send.offline

import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.policy.CompositeHardwareWalletTokenPolicy
import cash.p.terminal.tangem.domain.policy.TangemHardwareWalletTokenPolicy
import cash.p.terminal.trezor.domain.policy.TrezorHardwareWalletTokenPolicy
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Verifies the support matrix is fully derived from existing layers, with no hand-maintained list:
 * the bitcoin-like gate ([BtcBlockchainManager.blockchainTypes]), watch-only rejection,
 * [cash.p.terminal.core.supports] token/account compatibility, and the real hardware-wallet policy.
 */
class OfflineBroadcastTokenResolverTest {

    private val marketKit = mockk<MarketKitWrapper>()
    private val btcBlockchainManager = mockk<BtcBlockchainManager>()
    private val evmBlockchainManager = mockk<EvmBlockchainManager>()
    private val hardwareWalletTokenPolicy = CompositeHardwareWalletTokenPolicy(
        tangemPolicy = TangemHardwareWalletTokenPolicy(),
        trezorPolicy = TrezorHardwareWalletTokenPolicy(),
    )

    private val resolver = OfflineBroadcastTokenResolver(
        marketKit = marketKit,
        btcBlockchainManager = btcBlockchainManager,
        evmBlockchainManager = evmBlockchainManager,
        hardwareWalletTokenPolicy = hardwareWalletTokenPolicy,
    )

    @Before
    fun setUp() {
        every { btcBlockchainManager.blockchainTypes } returns BITCOIN_LIKE
        every { evmBlockchainManager.getBaseToken(any()) } answers {
            token(firstArg(), TokenType.Native)
        }
        // Echo each requested query back as an existing token so capability is decided purely by the
        // supports/policy layers, not by token availability.
        every { marketKit.token(any()) } answers {
            val query = firstArg<TokenQuery>()
            token(query.blockchainType, query.tokenType)
        }
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun resolveTokenToEnable_mnemonicOnBitcoin_returnsToken() {
        val token = resolver.resolveTokenToEnable(BlockchainType.Bitcoin, mnemonicAccount())

        assertNotNull(token)
        assertEquals(BlockchainType.Bitcoin, token?.blockchainType)
    }

    @Test
    fun resolveTokenToEnable_nonBroadcastableBlockchain_returnsNull() {
        assertNull(resolver.resolveTokenToEnable(BlockchainType.Monero, mnemonicAccount()))
    }

    @Test
    fun resolveTokenToEnable_watchEvmAddressOnEthereum_returnsToken() {
        val token = resolver.resolveTokenToEnable(
            BlockchainType.Ethereum,
            account(AccountType.EvmAddress("0x1234567890abcdef1234567890abcdef12345678")),
        )

        assertNotNull(token)
        assertEquals(BlockchainType.Ethereum, token?.blockchainType)
    }

    @Test
    fun resolveTokenToEnable_watchSolanaAddress_returnsToken() {
        val token = resolver.resolveTokenToEnable(
            BlockchainType.Solana,
            account(AccountType.SolanaAddress("11111111111111111111111111111111")),
        )

        assertNotNull(token)
        assertEquals(BlockchainType.Solana, token?.blockchainType)
    }

    @Test
    fun resolveTokenToEnable_watchTonAddress_returnsToken() {
        val token = resolver.resolveTokenToEnable(
            BlockchainType.Ton,
            account(AccountType.TonAddress("UQCYTBH7n8OnQ6BgOfdkNRWF7socLJb9U-JMRcoz3UpL_0V6")),
        )

        assertNotNull(token)
        assertEquals(BlockchainType.Ton, token?.blockchainType)
    }

    @Test
    fun resolveTokenToEnable_watchTronAddress_returnsToken() {
        val token = resolver.resolveTokenToEnable(
            BlockchainType.Tron,
            account(AccountType.TronAddress("TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7")),
        )

        assertNotNull(token)
        assertEquals(BlockchainType.Tron, token?.blockchainType)
    }

    @Test
    fun resolveTokenToEnable_watchPublicXpub_returnsNull() {
        val watch = account(AccountType.HdExtendedKey(PUBLIC_XPUB))

        assertNull(resolver.resolveTokenToEnable(BlockchainType.Bitcoin, watch))
    }

    @Test
    fun resolveTokenToEnable_privateXprvOnBitcoin_returnsToken() {
        val account = account(AccountType.HdExtendedKey(PRIVATE_XPRV))

        assertNotNull(resolver.resolveTokenToEnable(BlockchainType.Bitcoin, account))
    }

    @Test
    fun resolveTokenToEnable_privateXprvOnNativeDogecoin_returnsNull() {
        // Dogecoin only has a Native token, which Token.supports rejects for an HD extended key,
        // so the old hardcoded "!= Dogecoin" special case is covered by the supports layer.
        val account = account(AccountType.HdExtendedKey(PRIVATE_XPRV))

        assertNull(resolver.resolveTokenToEnable(BlockchainType.Dogecoin, account))
    }

    @Test
    fun resolveTokenToEnable_hardwareCardOnECash_returnsNull() {
        assertNull(resolver.resolveTokenToEnable(BlockchainType.ECash, hardwareCardAccount()))
    }

    @Test
    fun resolveTokenToEnable_trezorOnUnsupportedNetwork_returnsNull() {
        assertNull(resolver.resolveTokenToEnable(BlockchainType.PirateCash, trezorAccount()))
    }

    @Test
    fun resolveTokenToEnable_trezorOnSupportedNetwork_returnsToken() {
        assertNotNull(resolver.resolveTokenToEnable(BlockchainType.Bitcoin, trezorAccount()))
    }

    @Test
    fun resolveTokenToEnable_dashOnTrezorModelSupportingDash_returnsToken() {
        // Trezor One (T1B1) supports Dash, so the per-model policy must allow it.
        assertNotNull(resolver.resolveTokenToEnable(BlockchainType.Dash, trezorAccount(model = "T1B1")))
    }

    @Test
    fun resolveTokenToEnable_dashOnTrezorModelWithoutDash_returnsNull() {
        // Trezor Safe 3 (T3B1) does not support Dash; the old hardcoded list wrongly allowed it for all
        // Trezor models, so this guards that the per-model policy is honoured.
        assertNull(resolver.resolveTokenToEnable(BlockchainType.Dash, trezorAccount(model = "T3B1")))
    }

    private fun mnemonicAccount() = account(AccountType.Mnemonic(listOf("word"), ""))

    private fun hardwareCardAccount() = account(
        AccountType.HardwareCard(
            cardId = "card",
            backupCardsCount = 0,
            walletPublicKey = "pub",
            signedHashes = 0,
        )
    )

    private fun trezorAccount(model: String = "T3B1") = account(
        AccountType.TrezorDevice(
            deviceId = "device",
            model = model,
            firmwareVersion = "1.0.0",
            walletPublicKey = "pub",
        )
    )

    private fun account(type: AccountType) = Account(
        id = "id",
        name = "name",
        type = type,
        origin = AccountOrigin.Restored,
        level = 0,
    )

    private fun token(blockchainType: BlockchainType, tokenType: TokenType) = Token(
        coin = Coin(uid = blockchainType.uid, name = blockchainType.uid, code = blockchainType.uid),
        blockchain = Blockchain(blockchainType, blockchainType.uid, null),
        type = tokenType,
        decimals = 8,
    )

    private companion object {
        val BITCOIN_LIKE = listOf(
            BlockchainType.Bitcoin,
            BlockchainType.BitcoinCash,
            BlockchainType.Litecoin,
            BlockchainType.Dash,
            BlockchainType.Dogecoin,
            BlockchainType.PirateCash,
            BlockchainType.Cosanta,
            BlockchainType.ECash,
        )

        const val PUBLIC_XPUB =
            "xpub6CudKadFxkN6jXWcJDJSWzt4tNt86ThhYEjtcTywfD5nsYcySEEhfGugKDLnv14ZDNnYBVbfYXbNvRp8cNNw9JAfoMTeph1BqGWYZA4DBDi"
        const val PRIVATE_XPRV =
            "xprv9yvGv56N8NooX3S9CBmS9rwLLM3dgzyrB1pHp5aL6sYozkHptgvT7UbCTuyXF1HUAaPiG24iDBbnp7EQr8eSJkANf9EodqUiATBXrtAAHjj"
    }
}
