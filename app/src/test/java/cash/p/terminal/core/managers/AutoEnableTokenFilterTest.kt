package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoEnableTokenFilterTest {

    private val ethBlockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null)

    private fun token(
        type: TokenType,
        coinName: String,
        coinCode: String,
        coinImage: String? = null,
        decimals: Int = 18,
    ) = Token(
        coin = Coin(
            uid = "$coinCode-uid",
            name = coinName,
            code = coinCode,
            image = coinImage,
        ),
        blockchain = ethBlockchain,
        type = type,
        decimals = decimals,
    )

    @Test
    fun filterKnown_typeInKnownTokens_includesItWithMarketKitMetadata() {
        val type = TokenType.Eip20("0xUSDT")
        val known = listOf(token(type, "Tether", "USDT", coinImage = "img-url", decimals = 6))

        val result = filterKnownAutoEnableTokens(listOf(type), known)

        assertEquals(1, result.size)
        val info = result.first()
        assertEquals(type, info.type)
        assertEquals("Tether", info.coinName)
        assertEquals("USDT", info.coinCode)
        assertEquals(6, info.coinDecimals)
        assertEquals("img-url", info.coinImage)
    }

    @Test
    fun filterKnown_typeNotInKnownTokens_dropsIt() {
        val knownType = TokenType.Eip20("0xLEGIT")
        val unknownScamType = TokenType.Eip20("0xSCAM")
        val known = listOf(token(knownType, "Legit", "LGT"))

        val result = filterKnownAutoEnableTokens(
            listOf(knownType, unknownScamType),
            known,
        )

        assertEquals(1, result.size)
        assertEquals(knownType, result.first().type)
    }

    @Test
    fun filterKnown_allTypesUnknown_returnsEmpty() {
        val scamType = TokenType.Eip20("0xSCAM")

        val result = filterKnownAutoEnableTokens(listOf(scamType), emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun filterKnown_duplicateTypes_returnsDistinct() {
        val type = TokenType.Eip20("0xUSDT")
        val known = listOf(token(type, "Tether", "USDT"))

        val result = filterKnownAutoEnableTokens(listOf(type, type, type), known)

        assertEquals(1, result.size)
    }

    @Test
    fun filterKnown_emptyTokenTypes_returnsEmpty() {
        val known = listOf(token(TokenType.Eip20("0xUSDT"), "Tether", "USDT"))

        val result = filterKnownAutoEnableTokens(emptyList(), known)

        assertTrue(result.isEmpty())
    }

    @Test
    fun toEnabledWallets_emptyInput_returnsEmpty() = runTest {
        val userDeleted = mockk<UserDeletedWalletManager>()

        val result = emptyList<AutoEnableTokenInfo>().toEnabledWallets(
            accountId = "acc",
            blockchainType = BlockchainType.Ethereum,
            userDeletedWalletManager = userDeleted,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun toEnabledWallets_oneInfo_buildsOneWalletWithMetadata() = runTest {
        val type = TokenType.Eip20("0xUSDT")
        val info = AutoEnableTokenInfo(
            type = type,
            coinName = "Tether",
            coinCode = "USDT",
            coinDecimals = 6,
            coinImage = "img-url",
        )
        val userDeleted = mockk<UserDeletedWalletManager> {
            coEvery { isDeletedByUser(any(), any()) } returns false
        }

        val result = listOf(info).toEnabledWallets(
            accountId = "acc",
            blockchainType = BlockchainType.Ethereum,
            userDeletedWalletManager = userDeleted,
        )

        assertEquals(1, result.size)
        val wallet = result.single()
        assertEquals(TokenQuery(BlockchainType.Ethereum, type).id, wallet.tokenQueryId)
        assertEquals("acc", wallet.accountId)
        assertEquals("Tether", wallet.coinName)
        assertEquals("USDT", wallet.coinCode)
        assertEquals(6, wallet.coinDecimals)
        assertEquals("img-url", wallet.coinImage)
    }

    @Test
    fun toEnabledWallets_deletedByUser_skipsThatInfo() = runTest {
        val keepType = TokenType.Eip20("0xKEEP")
        val skipType = TokenType.Eip20("0xSKIP")
        val infos = listOf(
            AutoEnableTokenInfo(keepType, "Keep", "KEP", 18, null),
            AutoEnableTokenInfo(skipType, "Skip", "SKP", 18, null),
        )
        val userDeleted = mockk<UserDeletedWalletManager> {
            coEvery { isDeletedByUser("acc", TokenQuery(BlockchainType.Ethereum, keepType).id) } returns false
            coEvery { isDeletedByUser("acc", TokenQuery(BlockchainType.Ethereum, skipType).id) } returns true
        }

        val result = infos.toEnabledWallets(
            accountId = "acc",
            blockchainType = BlockchainType.Ethereum,
            userDeletedWalletManager = userDeleted,
        )

        assertEquals(1, result.size)
        assertEquals(TokenQuery(BlockchainType.Ethereum, keepType).id, result.single().tokenQueryId)
    }
}
