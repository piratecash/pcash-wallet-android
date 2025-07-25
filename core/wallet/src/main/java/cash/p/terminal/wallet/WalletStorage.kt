package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.runBlocking

class WalletStorage(
    private val marketKit: MarketKitWrapper,
    private val storage: IEnabledWalletStorage,
    private val getHardwarePublicKeyForWalletUseCase: GetHardwarePublicKeyForWalletUseCase
) : IWalletStorage {

    private val map: HashMap<Wallet, Long> = HashMap()

    override fun wallets(account: Account): List<Wallet> {
        val enabledWallets = storage.enabledWallets(account.id)
        map.clear()

        val queries = enabledWallets.mapNotNull {
            TokenQuery.fromId(it.tokenQueryId)?.let {
                if (it.blockchainType is BlockchainType.Unsupported) {
                    return@mapNotNull null
                } else {
                    it
                }
            }
        }
        val tokens = marketKit.tokens(queries)

        val blockchainUids = queries.map { it.blockchainType.uid }
        val blockchains = marketKit.blockchains(blockchainUids)

        return enabledWallets.mapNotNull { enabledWallet ->
            val tokenQuery = TokenQuery.fromId(enabledWallet.tokenQueryId) ?: return@mapNotNull null

            tokens.find { it.tokenQuery == tokenQuery }?.let { token ->
                val hardwarePublicKey = runBlocking {
                    getHardwarePublicKeyForWalletUseCase(
                        account = account,
                        blockchainType = token.blockchainType,
                        tokenType = token.type
                    )
                }
                return@mapNotNull Wallet(
                    token = token,
                    account = account,
                    hardwarePublicKey = hardwarePublicKey
                ).apply { map[this] = enabledWallet.id }
            }

            if (enabledWallet.coinName != null && enabledWallet.coinCode != null && enabledWallet.coinDecimals != null) {
                val coinUid = tokenQuery.customCoinUid
                val blockchain = blockchains.firstOrNull { it.uid == tokenQuery.blockchainType.uid }
                    ?: return@mapNotNull null

                val token = Token(
                    coin = Coin(
                        uid = coinUid,
                        name = enabledWallet.coinName,
                        code = enabledWallet.coinCode,
                        image = enabledWallet.coinImage
                    ),
                    blockchain = blockchain,
                    type = tokenQuery.tokenType,
                    decimals = enabledWallet.coinDecimals
                )

                val hardwarePublicKey = runBlocking {
                    getHardwarePublicKeyForWalletUseCase(
                        account = account,
                        blockchainType = token.blockchainType,
                        tokenType = token.type
                    )
                }

                Wallet(
                    token = token,
                    account = account,
                    hardwarePublicKey = hardwarePublicKey
                ).apply {
                    map[this] = enabledWallet.id
                }
            } else {
                null
            }
        }
    }

    override fun save(wallets: List<Wallet>) {
        val enabledWallets = mutableListOf<EnabledWallet>()

        wallets.forEachIndexed { index, wallet ->
            enabledWallets.add(
                enabledWallet(wallet, index)
            )
        }

        storage.save(enabledWallets).forEachIndexed { index, id ->
            map[wallets[index]] = id
        }
    }

    override fun delete(wallets: List<Wallet>) {
        storage.delete(wallets.mapNotNull { map[it] })
    }

    override fun handle(newEnabledWallets: List<EnabledWallet>) {
        storage.save(newEnabledWallets)
    }

    override fun clear() {
        storage.deleteAll()
    }

    private fun enabledWallet(wallet: Wallet, index: Int? = null): EnabledWallet {
        return EnabledWallet(
            tokenQueryId = wallet.token.tokenQuery.id,
            accountId = wallet.account.id,
            walletOrder = index,
            coinName = wallet.coin.name,
            coinCode = wallet.coin.code,
            coinDecimals = wallet.decimal,
            coinImage = wallet.coin.image
        )
    }
}
