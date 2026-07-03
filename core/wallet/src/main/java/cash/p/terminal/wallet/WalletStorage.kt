package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.entities.BlockchainType

class WalletStorage(
    private val marketKit: MarketKitWrapper,
    private val storage: IEnabledWalletStorage,
    private val getHardwarePublicKeyForWalletUseCase: GetHardwarePublicKeyForWalletUseCase,
    private val walletFactory: WalletFactory,
    private val deletedWalletChecker: IDeletedWalletChecker
) : IWalletStorage {

    private val map: HashMap<Wallet, Long> = HashMap()

    override suspend fun wallets(account: Account): List<Wallet> {
        val allEnabledWallets = storage.enabledWallets(account.id)

        // Filter out wallets that user has deleted to prevent them from reappearing
        val deletedTokenQueryIds = deletedWalletChecker.getDeletedTokenQueryIds(account.id)

        val enabledWallets = allEnabledWallets.filter { it.tokenQueryId !in deletedTokenQueryIds }

        map.clear()

        val enabledWalletsByQuery = enabledWallets.mapNotNull { enabledWallet ->
            TokenQuery.fromId(enabledWallet.tokenQueryId)?.let { tokenQuery ->
                if (tokenQuery.blockchainType is BlockchainType.Unsupported) {
                    return@mapNotNull null
                } else {
                    tokenQuery to enabledWallet
                }
            }
        }

        val queries = enabledWalletsByQuery
            .map { (tokenQuery, _) -> tokenQuery }
            .normalizedZcashWalletQueriesForLoad()

        val enabledWalletsByQueryId = enabledWalletsByQuery.associate { (tokenQuery, enabledWallet) ->
            tokenQuery.id to enabledWallet
        }

        val tokens = marketKit.tokens(queries)

        val blockchainUids = queries.map { it.blockchainType.uid }
        val blockchains = marketKit.blockchains(blockchainUids)

        return buildList {
            for (tokenQuery in queries) {
                val enabledWallet = enabledWalletsByQueryId[tokenQuery.id]

                val existingToken = tokens.find { it.tokenQuery == tokenQuery }
                if (existingToken != null) {
                    val hardwarePublicKey = getHardwarePublicKeyForWalletUseCase(
                        account = account,
                        blockchainType = existingToken.blockchainType,
                        tokenType = existingToken.type
                    )
                    walletFactory.create(
                        token = existingToken,
                        account = account,
                        hardwarePublicKey = hardwarePublicKey
                    )?.let { wallet ->
                        enabledWallet?.let { map[wallet] = it.id }
                        add(wallet)
                    }
                    continue
                }

                enabledWallet ?: continue

                if (enabledWallet.coinName != null && enabledWallet.coinCode != null && enabledWallet.coinDecimals != null) {
                    val coinUid = tokenQuery.customCoinUid
                    val blockchain = blockchains.firstOrNull { it.uid == tokenQuery.blockchainType.uid }
                        ?: continue

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

                    val hardwarePublicKey = getHardwarePublicKeyForWalletUseCase(
                        account = account,
                        blockchainType = token.blockchainType,
                        tokenType = token.type
                    )

                    walletFactory.create(
                        token = token,
                        account = account,
                        hardwarePublicKey = hardwarePublicKey
                    )?.let { wallet ->
                        map[wallet] = enabledWallet.id
                        add(wallet)
                    }
                }
            }
        }
    }

    override fun save(wallets: List<Wallet>) {
        if (wallets.isEmpty()) return

        val accountId = wallets.first().account.id
        val existingTokenIds = storage.enabledWallets(accountId)
            .mapTo(mutableSetOf()) { it.tokenQueryId }

        val walletsToAdd = wallets
            .filter { wallet ->
                wallet.token.tokenQuery.id !in existingTokenIds
            }
            .distinctBy { it.token.tokenQuery.id }

        if (walletsToAdd.isEmpty()) return

        val enabledWallets = walletsToAdd.mapIndexed { index, wallet ->
            enabledWallet(wallet, index)
        }

        handle(enabledWallets).forEachIndexed { index, id ->
            map[walletsToAdd[index]] = id
        }
    }

    override fun delete(wallets: List<Wallet>) {
        storage.delete(wallets.mapNotNull { map[it] })
    }

    override fun deleteByTokenQueryId(accountId: String, tokenQueryId: String) {
        storage.deleteByTokenQueryId(accountId, tokenQueryId)
    }

    override fun handle(newEnabledWallets: List<EnabledWallet>): List<Long> {
        return storage.save(newEnabledWallets)
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
