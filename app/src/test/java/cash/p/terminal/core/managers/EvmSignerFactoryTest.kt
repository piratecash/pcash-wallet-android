package cash.p.terminal.core.managers

import cash.p.terminal.core.installEthereumCryptoProviderForTest
import cash.p.terminal.tangem.common.CustomXPubKeyAddressParser
import cash.p.terminal.tangem.signer.HardwareWalletEvmSigner
import cash.p.terminal.trezor.signer.TrezorEvmSigner
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EvmSignerFactoryTest {

    // BIP32 test vector 1 master public extended key - a well-known, valid xpub.
    private val xPubKey =
        "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ES" +
                "FjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"

    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage = mockk()
    private val trezorClient: ITrezorClient = mockk()
    private val factory = EvmSignerFactory(hardwarePublicKeyStorage, trezorClient)

    private val hardwareAccount = Account(
        id = "hardware-account-id",
        name = "Tangem",
        type = AccountType.HardwareCard(
            cardId = "card-id",
            backupCardsCount = 0,
            walletPublicKey = "wallet-public-key",
            signedHashes = 0
        ),
        origin = AccountOrigin.Restored,
        level = 0
    )

    private val hardwarePublicKey = HardwarePublicKey(
        accountId = hardwareAccount.id,
        blockchainType = BlockchainType.Ethereum.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = TokenType.Native,
        key = SecretString(xPubKey),
        derivationPath = "m/44'/60'/0'/0/0",
        publicKey = byteArrayOf(1, 2, 3),
        derivedPublicKey = byteArrayOf(4, 5, 6)
    )

    // Same xpub (an EVM address is identical across token types) but stored under an ERC-20 token type,
    // representing an account that enabled only a token and therefore has no Native row.
    private val erc20OnlyKey = HardwarePublicKey(
        accountId = hardwareAccount.id,
        blockchainType = BlockchainType.Ethereum.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7"),
        key = SecretString(xPubKey),
        derivationPath = "m/44'/60'/0'/0/0",
        publicKey = byteArrayOf(1, 2, 3),
        derivedPublicKey = byteArrayOf(4, 5, 6)
    )

    @Before
    fun setUp() {
        installEthereumCryptoProviderForTest()
        coEvery {
            hardwarePublicKeyStorage.getKey(hardwareAccount.id, BlockchainType.Ethereum, TokenType.Native)
        } returns hardwarePublicKey
    }

    @Test
    fun createSigner_hardwareCardAccount_usesNativeTokenTypeAndBuildsHardwareSigner() = runBlocking {
        val signer = factory.createSigner(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)

        assertTrue(signer is HardwareWalletEvmSigner)
        coVerify(exactly = 1) {
            hardwarePublicKeyStorage.getKey(hardwareAccount.id, BlockchainType.Ethereum, TokenType.Native)
        }
    }

    @Test
    fun resolveAddress_hardwareCardAccount_usesNativeTokenTypeAndParsesAddress() = runBlocking {
        val expectedAddress = Address(CustomXPubKeyAddressParser.parse(xPubKey).addressBytes)

        val address = factory.resolveAddress(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)

        assertEquals(expectedAddress, address)
        coVerify(exactly = 1) {
            hardwarePublicKeyStorage.getKey(hardwareAccount.id, BlockchainType.Ethereum, TokenType.Native)
        }
    }

    @Test
    fun resolveAddress_hardwareWithoutNativeKey_fallsBackToAnyBlockchainKey() = runBlocking {
        // Account enabled only an ERC-20 token, so no Native row exists — only a blockchain key.
        coEvery {
            hardwarePublicKeyStorage.getKey(hardwareAccount.id, BlockchainType.Ethereum, TokenType.Native)
        } returns null
        coEvery {
            hardwarePublicKeyStorage.getKeyByBlockchain(hardwareAccount.id, BlockchainType.Ethereum)
        } returns erc20OnlyKey
        val expectedAddress = Address(CustomXPubKeyAddressParser.parse(xPubKey).addressBytes)

        val address = factory.resolveAddress(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)

        assertEquals(expectedAddress, address)
    }

    @Test
    fun createSigner_hardwareWithoutNativeKey_fallsBackToAnyBlockchainKey() = runBlocking {
        coEvery {
            hardwarePublicKeyStorage.getKey(hardwareAccount.id, BlockchainType.Ethereum, TokenType.Native)
        } returns null
        coEvery {
            hardwarePublicKeyStorage.getKeyByBlockchain(hardwareAccount.id, BlockchainType.Ethereum)
        } returns erc20OnlyKey

        val signer = factory.createSigner(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)

        assertTrue(signer is HardwareWalletEvmSigner)
    }

    @Test
    fun createSigner_mnemonicAccount_buildsBaseSignerWithoutHardwareStorage() = runBlocking {
        val account = mnemonicAccount()

        val signer = factory.createSigner(account, BlockchainType.Ethereum, Chain.Ethereum)

        assertTrue(signer !is HardwareWalletEvmSigner && signer !is TrezorEvmSigner)
        coVerify(exactly = 0) { hardwarePublicKeyStorage.getKey(any(), any(), any()) }
    }

    @Test
    fun resolveAddress_mnemonicAccount_matchesSignerAddress() = runBlocking {
        val account = mnemonicAccount()
        val expectedAddress = Signer.address((account.type as AccountType.Mnemonic).seed, Chain.Ethereum)

        val address = factory.resolveAddress(account, BlockchainType.Ethereum, Chain.Ethereum)

        assertEquals(expectedAddress, address)
    }

    private fun mnemonicAccount() = Account(
        id = "mnemonic-account-id",
        name = "Mnemonic",
        type = AccountType.Mnemonic(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "),
            ""
        ),
        origin = AccountOrigin.Created,
        level = 0
    )
}
