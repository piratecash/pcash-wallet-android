package cash.p.terminal.modules.paycore

import android.util.Base64
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.ethereumkit.core.signer.Signer as EthereumSigner
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.models.Chain
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
        val privateKey = extractPrivateKey(account)
        val walletAddress = resolveWalletAddress(privateKey, networkType)
        val timestamp = currentTimeSeconds().toString()
        val hash = buildSignedHash(url, timestamp, walletAddress, networkType)
        val signature = CryptoUtils.ellipticSign(hash, privateKey)

        return mapOf(
            X_SIGNATURE to Base64.encodeToString(signature, Base64.NO_WRAP),
            X_TIMESTAMP to timestamp,
            X_WALLET to walletAddress,
        )
    }

    fun getWalletAddress(networkType: PayCoreTicker): String {
        return getWalletAddress(networkType, activeAccount())
    }

    fun getWalletAddress(networkType: PayCoreTicker, account: Account): String {
        return resolveWalletAddress(extractPrivateKey(account), networkType)
    }

    private fun activeAccount(): Account {
        return requireNotNull(accountManager.activeAccount) { "No active account" }
    }

    private fun extractPrivateKey(account: Account): BigInteger {
        return when (val type = account.type) {
            is AccountType.Mnemonic -> EthereumSigner.privateKey(type.seed, Chain.Ethereum)
            is AccountType.EvmPrivateKey -> type.key
            else -> error("Unsupported account type for PayCore: ${type::class.simpleName}")
        }
    }

    private fun resolveWalletAddress(privateKey: BigInteger, networkType: PayCoreTicker): String {
        return when (networkType) {
            PayCoreTicker.USDT -> TronSigner.address(privateKey, Network.Mainnet).base58
            else -> EthereumSigner.address(privateKey).eip55
        }
    }

    private fun buildSignedHash(
        url: String,
        timestamp: String,
        walletAddress: String,
        networkType: PayCoreTicker,
    ): ByteArray {
        val payload = "$url\n$timestamp\n$walletAddress".toByteArray()
        val prefix = (signedMessagePrefix(networkType) + payload.size).toByteArray()
        return CryptoUtils.sha3(prefix + payload)
    }

    private fun signedMessagePrefix(networkType: PayCoreTicker): String = when (networkType) {
        PayCoreTicker.USDT -> TRON_SIGNED_MESSAGE_PREFIX
        else -> ETHEREUM_SIGNED_MESSAGE_PREFIX
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
