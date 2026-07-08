package cash.p.terminal.core.adapters

import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Locks that [BitcoinBaseAdapter.buildTrezorBtcSigner] resolves the signing key by the wallet's
 * [TokenType] (via [IHardwarePublicKeyStorage.getKey]) instead of falling back to
 * [IHardwarePublicKeyStorage.getKeyByBlockchain], which would return an arbitrary derivation
 * when a Trezor account has multiple Bitcoin derivations enabled.
 */
class TrezorBtcSignerBuildTest {

    private val accountId = "account-1"
    private val bip84Type = TokenType.Derived(TokenType.Derivation.Bip84)
    private val bip86Type = TokenType.Derived(TokenType.Derivation.Bip86)

    private val storage = mockk<IHardwarePublicKeyStorage>()
    private val trezorClient = mockk<ITrezorClient>(relaxed = true)

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            modules(module {
                single<IHardwarePublicKeyStorage> { storage }
                single<ITrezorClient> { trezorClient }
            })
        }

        coEvery {
            storage.getKey(accountId, BlockchainType.Bitcoin, bip86Type)
        } returns hardwareKey(bip86Type, "m/86'/0'/0'")
        coEvery {
            storage.getKey(accountId, BlockchainType.Bitcoin, bip84Type)
        } returns hardwareKey(bip84Type, "m/84'/0'/0'")
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun buildTrezorBtcSigner_multipleDerivationsEnabled_selectsKeyForTokenType() {
        BitcoinBaseAdapter.buildTrezorBtcSigner(accountId, BlockchainType.Bitcoin, bip86Type, "Bitcoin")

        coVerify(exactly = 1) { storage.getKey(accountId, BlockchainType.Bitcoin, bip86Type) }
        coVerify(exactly = 0) { storage.getKeyByBlockchain(any(), any()) }
    }

    @Test
    fun buildTrezorBtcSigner_bip84TokenType_selectsBip84Key() {
        BitcoinBaseAdapter.buildTrezorBtcSigner(accountId, BlockchainType.Bitcoin, bip84Type, "Bitcoin")

        coVerify(exactly = 1) { storage.getKey(accountId, BlockchainType.Bitcoin, bip84Type) }
        coVerify(exactly = 0) { storage.getKeyByBlockchain(any(), any()) }
    }

    private fun hardwareKey(tokenType: TokenType, derivationPath: String) = HardwarePublicKey(
        accountId = accountId,
        blockchainType = BlockchainType.Bitcoin.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = tokenType,
        key = SecretString("key"),
        derivationPath = derivationPath,
        publicKey = byteArrayOf(1, 2, 3),
        derivedPublicKey = byteArrayOf(4, 5, 6)
    )
}
