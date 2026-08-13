package cash.p.terminal.modules.subscription

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmMessageSigning
import cash.p.terminal.core.managers.EvmSignerFactory
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.SubscriptionManager
import cash.p.terminal.core.to0xHexString
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.net.UnknownHostException

class ActivateSubscriptionViewModel(
    private val marketKit: MarketKitWrapper,
    private val accountManager: IAccountManager,
    private val subscriptionManager: SubscriptionManager,
    private val dispatcherProvider: DispatcherProvider,
    private val evmSignerFactory: EvmSignerFactory,
    private val evmBlockchainManager: EvmBlockchainManager
) : ViewModelUiState<ActivateSubscription>() {

    private val ethChain get() = evmBlockchainManager.getChain(BlockchainType.Ethereum)

    private var subscriptionAccount: SubscriptionAccount? = null

    private var fetchingMessage = true
    private var fetchingMessageError: Throwable? = null
    private var subscriptionInfo: SubscriptionInfo? = null
    private var fetchingToken = false
    private var fetchingTokenError: Throwable? = null
    private var fetchingTokenSuccess = false

    init {
        fetchMessageToSign()
    }

    override fun createState() = ActivateSubscription(
        fetchingMessage = fetchingMessage,
        fetchingMessageError = fetchingMessageError,
        subscriptionInfo = subscriptionInfo,
        fetchingToken = fetchingToken,
        fetchingTokenError = fetchingTokenError,
        fetchingTokenSuccess = fetchingTokenSuccess,
        signButtonState = getSignButtonState()
    )

    private fun fetchMessageToSign() {
        viewModelScope.launch(dispatcherProvider.io) {
            fetchingMessage = true
            emitState()

            try {
                val accountsMap = accountManager.accounts
                    .filterNot { it.isWatchAccount }
                    .mapNotNull { account ->
                        evmSignerFactory.resolveAddress(
                            account, BlockchainType.Ethereum, ethChain
                        )?.hex?.let { address ->
                            Pair(address, account)
                        }
                    }.associateBy({ it.first }, { it.second })

                val addresses = accountsMap.keys.toList()
                val subscriptions = marketKit.subscriptionsSingle(addresses).await()
                val address =
                    subscriptions.maxByOrNull { it.deadline }?.address ?: throw NoSubscription()

                subscriptionAccount = SubscriptionAccount(address, accountsMap[address])

                val messageToSign = marketKit.authGetSignMessage(address).await()

                fetchingMessage = false
                fetchingMessageError = null
                subscriptionInfo = SubscriptionInfo(
                    walletName = accountsMap[address]?.name ?: "--",
                    walletAddress = address,
                    messageToSign = messageToSign
                )

            } catch (e: Throwable) {
                fetchingMessage = false
                fetchingMessageError = if (e is UnknownHostException) {
                    IllegalStateException(
                        cash.p.terminal.strings.helpers.Translator.getString(R.string.Hud_Text_NoInternet)
                    )
                } else {
                    e
                }
            }

            emitState()
        }
    }

    private fun getSignButtonState() = when {
        fetchingToken -> ButtonState.Disabled
        fetchingMessage -> ButtonState.Hidden
        subscriptionInfo != null -> ButtonState.Enabled
        else -> ButtonState.Hidden
    }

    fun sign() {
        val subscriptionInfo = subscriptionInfo ?: return
        val account = subscriptionAccount?.account ?: return
        val address = subscriptionAccount?.address ?: return

        fetchingToken = true
        emitState()

        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val signer = evmSignerFactory.createSigner(account, BlockchainType.Ethereum, ethChain)
                    ?: throw IllegalStateException()
                val signature =
                    EvmMessageSigning.signPersonalMessage(signer, subscriptionInfo.messageToSign.toByteArray())
                val token = marketKit.authenticate(signature.to0xHexString(), address).await()
                subscriptionManager.authToken = token
                fetchingTokenSuccess = true
            } catch (t: Throwable) {
                fetchingTokenError = t
            }

            fetchingToken = false
            emitState()
        }
    }

    fun retry() {
        fetchMessageToSign()
    }
}

data class SubscriptionAccount(
    val address: String,
    val account: cash.p.terminal.wallet.Account?
)

data class SubscriptionInfo(
    val walletName: String,
    val walletAddress: String,
    val messageToSign: String
)

data class ActivateSubscription(
    val fetchingMessage: Boolean,
    val fetchingMessageError: Throwable?,
    val subscriptionInfo: SubscriptionInfo?,
    val fetchingToken: Boolean,
    val fetchingTokenError: Throwable?,
    val fetchingTokenSuccess: Boolean,
    val signButtonState: ButtonState
)

enum class ButtonState(val visible: Boolean, val enabled: Boolean) {
    Enabled(true, true), Disabled(true, false), Hidden(false, true)
}

class NoSubscription : Exception()
