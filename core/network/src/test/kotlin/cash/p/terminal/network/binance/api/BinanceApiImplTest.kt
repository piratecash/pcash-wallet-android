package cash.p.terminal.network.binance.api

import cash.p.terminal.network.binance.data.TokenBalance
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BinanceApiImplTest {

    @MockK
    private lateinit var ethereumRpcApi: EthereumRpcApiImpl

    private lateinit var binanceApi: BinanceApiImpl

    private val contractAddress = "0xContract"
    private val walletAddress = "0xWallet"

    private val urlList = listOf(
        "https://bsc-dataseed.binance.org/",
        "https://bsc-dataseed1.defibit.io/",
        "https://bsc-dataseed1.ninicoin.io/",
        "https://bsc-dataseed2.defibit.io/",
        "https://bsc-dataseed3.defibit.io/",
        "https://bsc-dataseed4.defibit.io/",
        "https://bsc-dataseed2.ninicoin.io/",
        "https://bsc-dataseed3.ninicoin.io/",
        "https://bsc-dataseed4.ninicoin.io/",
        "https://bsc-dataseed1.binance.org/",
        "https://bsc-dataseed2.binance.org/",
        "https://bsc-dataseed3.binance.org/",
        "https://bsc-dataseed4.binance.org/"
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        binanceApi = BinanceApiImpl(ethereumRpcApi)
    }

    @Test
    fun `getTokenBalance tries all URLs when all throw exceptions`() = runTest {
        coEvery {
            ethereumRpcApi.getTokenBalance(any(), contractAddress, walletAddress)
        } throws RuntimeException("Network error")

        val result = binanceApi.getTokenBalance(contractAddress, walletAddress)

        assertNull(result)
        coVerify(exactly = urlList.size) {
            ethereumRpcApi.getTokenBalance(any(), contractAddress, walletAddress)
        }
        urlList.forEach { url ->
            coVerify(exactly = 1) {
                ethereumRpcApi.getTokenBalance(url, contractAddress, walletAddress)
            }
        }
    }

    @Test
    fun `getTokenBalance stops after second URL responds successfully`() = runTest {
        val firstUrl = urlList[0]
        val secondUrl = urlList[1]
        val hexBalance = "0x5F5E100" // 100_000_000 in hex (1.0 with 8 decimals)

        coEvery {
            ethereumRpcApi.getTokenBalance(firstUrl, contractAddress, walletAddress)
        } throws RuntimeException("First URL failed")

        coEvery {
            ethereumRpcApi.getTokenBalance(secondUrl, contractAddress, walletAddress)
        } returns hexBalance

        val result = binanceApi.getTokenBalance(contractAddress, walletAddress)

        assertNotNull(result)
        val expected = TokenBalance.fromHexBalance(hexBalance, TokenBalance.PIRATE_DECIMALS)
        assertEquals(expected.balance, result.balance)
        coVerify(exactly = 1) {
            ethereumRpcApi.getTokenBalance(firstUrl, contractAddress, walletAddress)
        }
        coVerify(exactly = 1) {
            ethereumRpcApi.getTokenBalance(secondUrl, contractAddress, walletAddress)
        }
        urlList.drop(2).forEach { url ->
            coVerify(exactly = 0) {
                ethereumRpcApi.getTokenBalance(url, contractAddress, walletAddress)
            }
        }
    }

    @Test
    fun `getTokenBalance returns result from first URL when it succeeds`() = runTest {
        val firstUrl = urlList[0]
        val hexBalance = "0x5F5E100" // 100_000_000 in hex (1.0 with 8 decimals)

        coEvery {
            ethereumRpcApi.getTokenBalance(firstUrl, contractAddress, walletAddress)
        } returns hexBalance

        val result = binanceApi.getTokenBalance(contractAddress, walletAddress)

        assertNotNull(result)
        val expected = TokenBalance.fromHexBalance(hexBalance, TokenBalance.PIRATE_DECIMALS)
        assertEquals(expected.balance, result.balance)
        coVerify(exactly = 1) {
            ethereumRpcApi.getTokenBalance(firstUrl, contractAddress, walletAddress)
        }
        urlList.drop(1).forEach { url ->
            coVerify(exactly = 0) {
                ethereumRpcApi.getTokenBalance(url, contractAddress, walletAddress)
            }
        }
    }

    @Test
    fun `getTokenBalance returns null when all URLs return null`() = runTest {
        coEvery {
            ethereumRpcApi.getTokenBalance(any(), contractAddress, walletAddress)
        } returns null

        val result = binanceApi.getTokenBalance(contractAddress, walletAddress)

        assertNull(result)
        coVerify(exactly = urlList.size) {
            ethereumRpcApi.getTokenBalance(any(), contractAddress, walletAddress)
        }
    }
}
