package cash.p.terminal.modules.paycore

import android.util.Base64
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import com.solana.core.HotAccount
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.solanakit.Signer as SolanaSigner
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.transaction.Signer as TronSigner
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

    // ERC vectors are taken against a fixed single EVM private key; the ERC signing
    // path is unchanged by the per-network fix, so the vectors still hold.
    private val privateKey = BigInteger(
        "7c7ab0b8e74b5b036bb02701d9ef8acd9251bfa13672858afb8ef60116ace488",
        16,
    )
    private val evmAddress = "0xeC9F8465eE8A0eE9dB6Bd61029100D618057ADD9"
    private val url = "https://pirate.paycore.pw/api/v2/wallet/create"
    private val timestampSeconds = 1_776_643_200L

    private val expectedErcSignatureHex =
        "20fb29c77ac5aaeaeeed597123392311e901ffc901bc55348af7ab07b96a74e0" +
            "24f528c4425106013fe0ccae125eab8cc258984b27f5b940376343add2345ec5" +
            "00"

    private val accountManager = mockk<IAccountManager>()

    private val evmAccount = Account(
        id = "evm",
        name = "Evm",
        type = AccountType.EvmPrivateKey(privateKey),
        origin = AccountOrigin.Created,
        level = 0,
    )

    // Standard BIP39 test vector mnemonic (valid checksum) — TRON and Solana wallets
    // exist only for mnemonic accounts, so both networks must be signed from a seed.
    private val mnemonicAccount = Account(
        id = "mnemonic",
        name = "Mnemonic",
        type = AccountType.Mnemonic(
            words = ("abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon about").split(" "),
            passphrase = "",
        ),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val seed get() = (mnemonicAccount.type as AccountType.Mnemonic).seed

    private lateinit var helper: PayCoreSignatureHelper

    companion object {
        const val TRON_PREFIX = "\u0019TRON Signed Message:\n"

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

        every { accountManager.activeAccount } returns evmAccount

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
        assertEquals(evmAddress, helper.getWalletAddress(PayCoreTicker.USDT_ERC20))
    }

    @Test
    fun getWalletAddress_trc20_returnsTronAddressDerivedFromTronPath() {
        every { accountManager.activeAccount } returns mnemonicAccount

        val expected = TronSigner.address(
            TronSigner.privateKey(seed, Network.Mainnet),
            Network.Mainnet,
        ).base58

        assertEquals(expected, helper.getWalletAddress(PayCoreTicker.USDT))
    }

    @Test
    fun getWalletAddress_spl_returnsSolanaAddress() {
        every { accountManager.activeAccount } returns mnemonicAccount

        assertEquals(SolanaSigner.address(seed), helper.getWalletAddress(PayCoreTicker.USDT_SPL))
    }

    @Test
    fun getSignedHeaders_erc20_matchesSpec() {
        val headers = helper.getSignedHeaders(url, PayCoreTicker.USDT_ERC20)

        assertEquals(evmAddress, headers["X-Wallet"])
        assertEquals(timestampSeconds.toString(), headers["X-Timestamp"])
        assertEquals(base64(expectedErcSignatureHex.hexStringToByteArray()), headers["X-Signature"])
    }

    @Test
    fun getSignedHeaders_trc20_signsWithTronKeyOfRegisteredAddress() {
        every { accountManager.activeAccount } returns mnemonicAccount

        val tronKey = TronSigner.privateKey(seed, Network.Mainnet)
        val tronAddress = TronSigner.address(tronKey, Network.Mainnet).base58
        val expectedSignature = secp256k1Signature(TRON_PREFIX, tronAddress, tronKey)

        val headers = helper.getSignedHeaders(url, PayCoreTicker.USDT)

        assertEquals(tronAddress, headers["X-Wallet"])
        assertEquals(timestampSeconds.toString(), headers["X-Timestamp"])
        assertEquals(expectedSignature, headers["X-Signature"])
    }

    @Test
    fun getSignedHeaders_spl_signsRawPayloadWithSolanaEd25519Key() {
        every { accountManager.activeAccount } returns mnemonicAccount

        val solanaAddress = SolanaSigner.address(seed)
        val payload = "$url\n$timestampSeconds\n$solanaAddress".toByteArray()
        val expectedSignature = base64(HotAccount(SolanaSigner.privateKey(seed)).sign(payload))

        val headers = helper.getSignedHeaders(url, PayCoreTicker.USDT_SPL)

        assertEquals(solanaAddress, headers["X-Wallet"])
        assertEquals(timestampSeconds.toString(), headers["X-Timestamp"])
        assertEquals(expectedSignature, headers["X-Signature"])
    }

    private fun secp256k1Signature(prefix: String, address: String, key: BigInteger): String {
        val payload = "$url\n$timestampSeconds\n$address".toByteArray()
        val hashInput = (prefix + payload.size).toByteArray() + payload
        return base64(CryptoUtils.ellipticSign(CryptoUtils.sha3(hashInput), key))
    }

    private fun base64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

}
