package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.R
import cash.p.terminal.core.ISendEthereumAdapter
import cash.p.terminal.core.cache.accountScoped
import cash.p.terminal.core.isEvm
import cash.p.terminal.core.isUtxoBased
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.multiswap.action.ActionCreate
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Address
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

class OffChainSwapProviderSupport(
    private val walletUseCase: WalletUseCase,
    private val accountManager: IAccountManager,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    private val marketKit: MarketKitWrapper,
    private val adapterManager: IAdapterManager,
    private val swapProviderTransactionFactory: SwapProviderTransactionFactory,
) {
    private var zcashTransparentAddress: String? by accountManager.accountScoped()
    private val zcashAddressMutex = Mutex()

    fun getCreateTokenActionRequired(
        tokens: List<Token>,
        useTransparentZcashRefundAddress: Boolean = true,
    ): ActionCreate? {
        val tokenIn = tokens.firstOrNull()
        val tokenZCashToCreate = if (
            tokenIn != null &&
            useTransparentZcashRefundAddress &&
            requiresTransparentRefundAddress(tokenIn)
        ) {
            getZCashTransparentToken()
        } else {
            null
        }
        val needCreateTransparentWallet =
            tokenZCashToCreate != null && walletUseCase.getWallet(tokenZCashToCreate) == null
        val tokensToAdd = tokens.filterTo(mutableSetOf()) { token ->
            walletUseCase.getWallet(token) == null
        }
        if (needCreateTransparentWallet) {
            tokensToAdd.add(tokenZCashToCreate)
        }

        return if (tokensToAdd.isNotEmpty()) {
            ActionCreate(
                inProgress = false,
                descriptionResId = if (!needCreateTransparentWallet) {
                    R.string.swap_create_wallet_description
                } else {
                    R.string.swap_create_wallet_description_with_zcash
                },
                tokensToAdd = tokensToAdd
            )
        } else {
            null
        }
    }

    fun getCreateTokenActionRequired(
        tokenIn: Token,
        tokenOut: Token,
        useTransparentZcashRefundAddress: Boolean = true,
    ): ActionCreate? {
        return getCreateTokenActionRequired(
            tokens = listOf(tokenIn, tokenOut),
            useTransparentZcashRefundAddress = useTransparentZcashRefundAddress,
        )
    }

    suspend fun getWarningMessage(
        tokenIn: Token,
        useTransparentZcashRefundAddress: Boolean = true,
    ): TranslatableString? {
        if (!useTransparentZcashRefundAddress || !requiresTransparentRefundAddress(tokenIn)) return null

        val refundAddress = getCachedZcashTransparentAddress() ?: return null

        return TranslatableString.ResString(R.string.zec_transparent_used, refundAddress)
    }

    suspend fun getRefundAddress(
        tokenIn: Token,
        useTransparentZcashRefundAddress: Boolean = true,
    ): String {
        if (!useTransparentZcashRefundAddress || !requiresTransparentRefundAddress(tokenIn)) {
            return walletUseCase.getReceiveAddress(tokenIn)
        }

        return getCachedZcashTransparentAddress()
            ?: throw IllegalStateException("Can't find ZCASH transparent wallet")
    }

    fun buildSwapProviderTransaction(
        provider: SwapProvider,
        transactionId: String,
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        amountOut: BigDecimal,
        subProviderId: String? = null,
    ) = swapProviderTransactionFactory.build(
        provider, transactionId, tokenIn, tokenOut, amountIn, amountOut, subProviderId = subProviderId
    )

    fun buildTransactionData(
        tokenIn: Token,
        amountIn: BigDecimal,
        depositAddress: String,
        memo: String?,
    ): SendTransactionData {
        return when {
            tokenIn.blockchainType.isEvm -> {
                val adapter = adapterManager.getAdapterForToken<ISendEthereumAdapter>(tokenIn)
                    ?: throw IllegalStateException("Ethereum adapter not found")
                val transactionData = adapter.getTransactionData(
                    amountIn,
                    Address(depositAddress)
                )

                SendTransactionData.Evm(transactionData, null, amount = amountIn)
            }

            tokenIn.blockchainType == BlockchainType.Tron -> {
                SendTransactionData.Tron.Regular(
                    amount = amountIn,
                    address = depositAddress
                )
            }

            tokenIn.blockchainType == BlockchainType.Stellar -> {
                SendTransactionData.Stellar.Regular(
                    amount = amountIn,
                    address = depositAddress,
                    memo = memo.orEmpty()
                )
            }

            tokenIn.blockchainType == BlockchainType.Solana -> {
                SendTransactionData.Solana.Regular(
                    amount = amountIn,
                    address = depositAddress
                )
            }

            tokenIn.blockchainType.isUtxoBased -> {
                SendTransactionData.Btc(
                    address = depositAddress,
                    memo = memo.orEmpty(),
                    amount = amountIn,
                    recommendedGasRate = null,
                    minimumSendAmount = null,
                    changeToFirstInput = false,
                    utxoFilters = UtxoFilters(),
                    feesMap = emptyMap()
                )
            }

            tokenIn.blockchainType == BlockchainType.Ton -> {
                SendTransactionData.Ton(
                    amount = amountIn,
                    address = depositAddress,
                    memo = memo
                )
            }

            tokenIn.blockchainType == BlockchainType.Monero -> {
                SendTransactionData.Monero(
                    amount = amountIn,
                    address = depositAddress
                )
            }

            else -> SendTransactionData.Unsupported
        }
    }

    fun onTransactionCompleted(
        transaction: SwapProviderTransaction,
        result: SendTransactionResult,
        depositTransactionHash: String? = null,
    ) {
        swapProviderTransactionsStorage.save(
            transaction.copy(
                outgoingRecordUid = result.getRecordUid(),
                date = System.currentTimeMillis(),
                depositTransactionHash = depositTransactionHash ?: transaction.depositTransactionHash,
            )
        )
    }

    private suspend fun getCachedZcashTransparentAddress(): String? =
        zcashAddressMutex.withLock {
            zcashTransparentAddress ?: initializeZcashAddress()
        }

    private suspend fun initializeZcashAddress(): String? {
        val transparentToken = getZCashTransparentToken() ?: return null
        val address = tryOrNull {
            walletUseCase.getOneTimeReceiveAddress(transparentToken)
        } ?: return null

        zcashTransparentAddress = address
        return address
    }

    private fun getZCashTransparentToken() = marketKit.token(
        TokenQuery(
            BlockchainType.Zcash,
            TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent)
        )
    )

    private fun requiresTransparentRefundAddress(tokenIn: Token): Boolean =
        tokenIn.blockchainType == BlockchainType.Zcash &&
                (tokenIn.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified) ||
                        tokenIn.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded))
}
