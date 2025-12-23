package cash.p.terminal.modules.addtoken

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.order
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class AddTokenService(
    private val coinManager: ICoinManager,
    private val walletManager: IWalletManager,
    private val accountManager: IAccountManager,
    private val userDeletedWalletManager: UserDeletedWalletManager,
    marketKit: MarketKitWrapper,
) {
    private val getHardwarePublicKeyForWalletUseCase: GetHardwarePublicKeyForWalletUseCase by inject(
        GetHardwarePublicKeyForWalletUseCase::class.java
    )
    private val walletFactory: WalletFactory by inject(WalletFactory::class.java)

    private val blockchainTypes = listOf(
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Tron,
        BlockchainType.Ton,
        BlockchainType.Polygon,
        BlockchainType.Avalanche,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.ArbitrumOne,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.Solana
    )

    val blockchains = marketKit
        .blockchains(blockchainTypes.map { it.uid })
        .sortedBy { it.type.order }

    val accountType = accountManager.activeAccount?.type

    suspend fun tokenInfo(blockchain: Blockchain, reference: String): TokenInfo? {
        if (reference.isEmpty()) return null

        val blockchainService = when (blockchain.type) {

            BlockchainType.Tron -> {
                AddTronTokenBlockchainService.getInstance(blockchain)
            }

            BlockchainType.Ton -> {
                AddTonTokenBlockchainService(blockchain)
            }

            BlockchainType.Solana -> {
                AddSolanaTokenBlockchainService.getInstance(blockchain)
            }

            else -> AddEvmTokenBlockchainService.getInstance(blockchain)
        }

        if (!blockchainService.isValid(reference)) throw TokenError.InvalidReference

        val token = coinManager.getToken(blockchainService.tokenQuery(reference))
        if (token != null && token.type !is TokenType.Unsupported) {
            return TokenInfo(token, true)
        }

        try {
            val customToken = blockchainService.token(reference)
            return TokenInfo(customToken, false)
        } catch (e: Throwable) {
            throw TokenError.NotFound
        }
    }

    suspend fun addToken(token: TokenInfo) = withContext(Dispatchers.IO) {
        val account = accountManager.activeAccount ?: return@withContext

        // Clear deletion flag so previously deleted token can reappear
        userDeletedWalletManager.unmarkAsDeleted(account.id, token.token.tokenQuery.id)

        val hardwarePublicKey = getHardwarePublicKeyForWalletUseCase(
                account,
                token.token.blockchainType,
                token.token.type
            )
        walletFactory.create(token.token, account, hardwarePublicKey)?.let {
            walletManager.save(listOf(it))
        }
    }

    sealed class TokenError : Exception() {
        object InvalidReference : TokenError()
        object NotFound : TokenError()
    }

    data class TokenInfo(
        val token: Token,
        val inCoinList: Boolean,
    )
}
