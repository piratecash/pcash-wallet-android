package cash.p.terminal.modules.paycore

import android.util.Base64
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import com.solana.core.Account as SolanaAccount
import com.solana.core.HotAccount
import io.horizontalsystems.ethereumkit.core.signer.Signer as EthereumSigner
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.solanakit.Signer as SolanaSigner
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.transaction.Signer as TronSigner
import java.math.BigInteger

class PayCoreSignatureHelper(
    private val accountManager: IAccountManager,
    private val currentTimeSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    fun getSignedHeaders(url: String, networkType: PayCoreTicker): Map<String, String> {
        return getSignedHeaders(url, networkType, activeAccount())
    }

    fun getSignedHeaders(url: String, networkType: PayCoreTicker, account: Account): Map<String, String> {
        val signer = createSigner(account, networkType)
        val timestamp = currentTimeSeconds().toString()
        val payload = "$url\n$timestamp\n${signer.address}".toByteArray()

        return mapOf(
            X_SIGNATURE to Base64.encodeToString(signer.sign(payload), Base64.NO_WRAP),
            X_TIMESTAMP to timestamp,
            X_WALLET to signer.address,
        )
    }

    fun getWalletAddress(networkType: PayCoreTicker): String {
        return getWalletAddress(networkType, activeAccount())
    }

    fun getWalletAddress(networkType: PayCoreTicker, account: Account): String {
        return createSigner(account, networkType).address
    }

    private fun activeAccount(): Account {
        return requireNotNull(accountManager.activeAccount) { "No active account" }
    }

    // PayCore verifies ownership of a freshly created wallet by checking that the
    // request signature comes from the very address being registered. Each network
    // must therefore sign with the key derived exactly like its own wallet adapter
    // derives it (path + curve), not with the Ethereum key for every chain.
    private fun createSigner(account: Account, networkType: PayCoreTicker): PayCoreSigner {
        return when (networkType) {
            PayCoreTicker.USDT -> {
                val privateKey = TronSigner.privateKey(mnemonicSeed(account), Network.Mainnet)
                Secp256k1Signer(
                    privateKey = privateKey,
                    address = TronSigner.address(privateKey, Network.Mainnet).base58,
                    messagePrefix = TRON_SIGNED_MESSAGE_PREFIX,
                )
            }

            PayCoreTicker.USDT_SPL -> {
                val seed = mnemonicSeed(account)
                Ed25519Signer(
                    account = HotAccount(SolanaSigner.privateKey(seed)),
                    address = SolanaSigner.address(seed),
                )
            }

            else -> {
                val privateKey = evmPrivateKey(account)
                Secp256k1Signer(
                    privateKey = privateKey,
                    address = EthereumSigner.address(privateKey).eip55,
                    messagePrefix = ETHEREUM_SIGNED_MESSAGE_PREFIX,
                )
            }
        }
    }

    private fun evmPrivateKey(account: Account): BigInteger {
        return when (val type = account.type) {
            is AccountType.Mnemonic -> EthereumSigner.privateKey(type.seed, Chain.Ethereum)
            is AccountType.EvmPrivateKey -> type.key
            else -> unsupportedAccount(type)
        }
    }

    private fun mnemonicSeed(account: Account): ByteArray {
        val type = account.type
        return (type as? AccountType.Mnemonic)?.seed ?: unsupportedAccount(type)
    }

    private fun unsupportedAccount(type: AccountType): Nothing {
        error("Unsupported account type for PayCore: ${type::class.simpleName}")
    }

    private sealed interface PayCoreSigner {
        val address: String
        fun sign(payload: ByteArray): ByteArray
    }

    // EVM and TRON share the secp256k1 curve and the EIP-191/TIP-191 personal_sign
    // scheme; only the domain-separator prefix and the derived key differ.
    private class Secp256k1Signer(
        private val privateKey: BigInteger,
        override val address: String,
        private val messagePrefix: String,
    ) : PayCoreSigner {
        override fun sign(payload: ByteArray): ByteArray {
            val prefix = (messagePrefix + payload.size).toByteArray()
            return CryptoUtils.ellipticSign(CryptoUtils.sha3(prefix + payload), privateKey)
        }
    }

    // Solana signs the raw payload with ed25519 — no keccak hashing and no
    // secp256k1-style domain-separator prefix.
    private class Ed25519Signer(
        private val account: SolanaAccount,
        override val address: String,
    ) : PayCoreSigner {
        override fun sign(payload: ByteArray): ByteArray = account.sign(payload)
    }

    private companion object {
        const val X_SIGNATURE = "X-Signature"
        const val X_TIMESTAMP = "X-Timestamp"
        const val X_WALLET = "X-Wallet"
        // Leading \u0019 byte is the EIP-191 / TIP-191 personal_sign domain separator.
        // It guarantees the hashed payload can never collide with a valid RLP-encoded
        // transaction, so the signature cannot be replayed as an on-chain tx.
        const val ETHEREUM_SIGNED_MESSAGE_PREFIX = "\u0019Ethereum Signed Message:\n"
        const val TRON_SIGNED_MESSAGE_PREFIX = "\u0019TRON Signed Message:\n"
    }
}
