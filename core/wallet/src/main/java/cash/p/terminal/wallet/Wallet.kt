package cash.p.terminal.wallet

import android.os.Parcelable
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.Objects

@ConsistentCopyVisibility
@Parcelize
data class Wallet internal constructor(
    val token: Token,
    val account: Account,
    val hardwarePublicKey: HardwarePublicKey? // used only for hardware wallets
) : Parcelable {
    val coin
        get() = token.coin

    val decimal
        get() = token.decimals

    val badge
        get() = token.badge

    val transactionSource get() = TransactionSource(token.blockchain, account, token.type.meta)

    override fun equals(other: Any?): Boolean {
        if (other is Wallet) {
            return token == other.token && account == other.account && hardwarePublicKey == other.hardwarePublicKey
        }

        return super.equals(other)
    }

    override fun hashCode(): Int {
        return Objects.hash(token, account, hardwarePublicKey)
    }

    fun getHDExtendedKey(): HDExtendedKey? {
        return hardwarePublicKey?.key?.value?.let {
            if (hardwarePublicKey.type == HardwarePublicKeyType.PUBLIC_KEY) {
                HDExtendedKey(it)
            } else {
                null
            }
        }
    }
}

val Wallet.tokenQueryId: String
    get() = token.tokenQuery.id

class WalletFactory(
    private val hardwareWalletTokenPolicy: HardwareWalletTokenPolicy
) {

    companion object {
        /***
         * Preview wallet for Compose previews
         */
        fun previewWallet(): Wallet = previewWallet(
            coinName = "Preview Coin",
            coinCode = "PCN",
            coinUid = "code",
            blockchainType = BlockchainType.Ethereum,
            blockchainName = "Ethereum",
            tokenType = TokenType.Native,
        )

        /***
         * Preview wallet that satisfies [isStakingWallet] (PCASH on BNB Smart Chain).
         */
        fun previewStakingWallet(): Wallet = previewWallet(
            coinName = "PirateCash",
            coinCode = "PCASH",
            coinUid = "pirate-cash",
            blockchainType = BlockchainType.BinanceSmartChain,
            blockchainName = "BNB Smart Chain",
            tokenType = TokenType.Eip20(BuildConfig.PIRATE_CONTRACT),
        )

        private fun previewWallet(
            coinName: String,
            coinCode: String,
            coinUid: String,
            blockchainType: BlockchainType,
            blockchainName: String,
            tokenType: TokenType,
        ): Wallet {
            val token = Token(
                coin = Coin(coinName, coinCode, coinUid),
                blockchain = Blockchain(
                    type = blockchainType,
                    name = blockchainName,
                    eip3091url = null
                ),
                type = tokenType,
                decimals = 8
            )
            val account = Account(
                id = "preview-account-id",
                name = "Preview Account",
                type = AccountType.EvmAddress("0x"),
                origin = AccountOrigin.Created,
                level = 0
            )
            return Wallet(token, account, null)
        }
    }

    fun create(token: Token, account: Account, hardwarePublicKey: HardwarePublicKey?): Wallet? {
        if (!account.type.isCompatibleWith(token.blockchainType, token.type)) {
            Timber.d(
                "Skipping wallet creation for token ${token.blockchainType} ${token.type} - account type ${account.type::class.simpleName} is not supported"
            )
            return null
        }

        if (account.isHardwareWalletAccount &&
            !hardwareWalletTokenPolicy.isSupported(account, token)
        ) {
            Timber.d(
                "Skipping wallet creation for token ${token.blockchainType} ${token.type} - hardware wallet not supported"
            )
            return null
        }

        return Wallet(token, account, hardwarePublicKey)
    }
}
