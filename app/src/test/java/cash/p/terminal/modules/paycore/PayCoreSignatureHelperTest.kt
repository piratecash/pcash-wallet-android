package cash.p.terminal.modules.paycore

import android.util.Base64
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigInteger

class PayCoreSignatureHelperTest {

    // Test vectors from altyn_api/sign.md
    private val privateKey = BigInteger(
        "7c7ab0b8e74b5b036bb02701d9ef8acd9251bfa13672858afb8ef60116ace488",
        16,
    )
    private val evmAddress = "0xeC9F8465eE8A0eE9dB6Bd61029100D618057ADD9"
    private val tronAddress = "TXYMZn8j7nGbaSBgwaLNwVdz1RmJdxRve6"
    private val url = "https://pirate.paycore.pw/api/v2/wallet/create"
    private val timestampSeconds = 1_776_643_200L

    private val expectedErcSignatureHex =
        "20fb29c77ac5aaeaeeed597123392311e901ffc901bc55348af7ab07b96a74e0" +
            "24f528c4425106013fe0ccae125eab8cc258984b27f5b940376343add2345ec5" +
            "00"
    private val expectedTrcSignatureHex =
        "57abc3c75b8ddd0f62de0a841bfd581ee4df534d437854648825499e1dbec46e" +
            "525bd45da03e031a49af1b9adacd7545eb60ce2af23bb95d22a6b01c8023ae61" +
            "01"

    private val accountManager = mockk<IAccountManager>()

    private val account = Account(
        id = "test",
        name = "Test",
        type = AccountType.EvmPrivateKey(privateKey),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private lateinit var helper: PayCoreSignatureHelper

    companion object {
        @JvmStatic
        @BeforeClass
        fun registerBouncyCastle() {
            EthereumKit.init()
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }

        every { accountManager.activeAccount } returns account

        helper = PayCoreSignatureHelper(
            accountManager = accountManager,
            currentTimeSeconds = { timestampSeconds },
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getWalletAddress_erc20_returnsChecksumEvmAddress() {
        assertEquals(evmAddress, helper.getWalletAddress(PayCoreNetworkType.ERC20))
    }

    @Test
    fun getWalletAddress_trc20_returnsBase58TronAddress() {
        assertEquals(tronAddress, helper.getWalletAddress(PayCoreNetworkType.TRC20))
    }

    @Test
    fun getSignedHeaders_erc20_matchesSpec() {
        val headers = helper.getSignedHeaders(url, PayCoreNetworkType.ERC20)

        assertEquals(evmAddress, headers["X-Wallet"])
        assertEquals(timestampSeconds.toString(), headers["X-Timestamp"])
        assertEquals(expectedSignatureBase64(expectedErcSignatureHex), headers["X-Signature"])
    }

    @Test
    fun getSignedHeaders_trc20_matchesSpec() {
        val headers = helper.getSignedHeaders(url, PayCoreNetworkType.TRC20)

        assertEquals(tronAddress, headers["X-Wallet"])
        assertEquals(timestampSeconds.toString(), headers["X-Timestamp"])
        assertEquals(expectedSignatureBase64(expectedTrcSignatureHex), headers["X-Signature"])
    }

    private fun expectedSignatureBase64(hex: String): String {
        return java.util.Base64.getEncoder().encodeToString(hex.hexStringToByteArray())
    }
}
