package cash.p.terminal.modules.eip20allowance

import cash.p.terminal.R
import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.core.adapters.Eip20Adapter
import cash.p.terminal.core.adapters.Trc20Adapter
import cash.p.terminal.core.isEvm
import cash.p.terminal.modules.eip20approve.AllowanceMode
import cash.p.terminal.modules.eip20approve.AllowanceMode.OnlyRequired
import cash.p.terminal.modules.eip20approve.AllowanceMode.Unlimited
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceEvm
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceTron
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigDecimal

internal object Eip20AllowanceSendTransactionFactory {

    fun emptyServiceState() = SendTransactionServiceState(
        availableBalance = null,
        networkFee = null,
        cautions = emptyList(),
        sendable = false,
        loading = false,
        fields = emptyList()
    )

    fun createSendTransactionService(token: Token): ISendTransactionService<*> = when {
        token.isEvmEip20 -> SendTransactionServiceEvm(token)
        token.isTronEip20 -> SendTransactionServiceTron(token)
        else -> unsupportedToken(token)
    }

    fun buildApproveTransactionData(
        token: Token,
        spenderAddress: String,
        amount: BigDecimal,
        allowanceMode: AllowanceMode,
        adapterManager: IAdapterManager,
    ): SendTransactionData = when {
        token.isEvmEip20 -> {
            val adapter = getEvmAdapter(token, adapterManager)
            val transactionData = when (allowanceMode) {
                OnlyRequired -> adapter.buildApproveTransactionData(Address(spenderAddress), amount)
                Unlimited -> adapter.buildApproveUnlimitedTransactionData(Address(spenderAddress))
            }
            SendTransactionData.Evm(transactionData, null)
        }

        token.isTronEip20 -> {
            val adapter = getTronAdapter(token, adapterManager)
            val contract = when (allowanceMode) {
                OnlyRequired -> adapter.approveTrc20TriggerSmartContract(spenderAddress, amount)
                Unlimited -> adapter.approveTrc20TriggerSmartContractUnlim(spenderAddress)
            }
            SendTransactionData.Tron.WithContract(contract)
        }

        else -> unsupportedToken(token)
    }

    fun buildRevokeTransactionData(
        token: Token,
        spenderAddress: String,
        adapterManager: IAdapterManager,
    ): SendTransactionData = when {
        token.isEvmEip20 -> {
            val adapter = getEvmAdapter(token, adapterManager)
            SendTransactionData.Evm(adapter.buildRevokeTransactionData(Address(spenderAddress)), null)
        }

        token.isTronEip20 -> {
            val adapter = getTronAdapter(token, adapterManager)
            val contract = adapter.approveTrc20TriggerSmartContract(spenderAddress, BigDecimal.ZERO)
            SendTransactionData.Tron.WithContract(contract)
        }

        else -> unsupportedToken(token)
    }

    fun userMessage(error: Throwable): String = when (error) {
        is UnsupportedException -> Translator.getString(R.string.unsupported_token)
        else -> error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    }

    private val Token.isEvmEip20: Boolean
        get() = type is TokenType.Eip20 && blockchainType.isEvm

    private val Token.isTronEip20: Boolean
        get() = type is TokenType.Eip20 && blockchainType == BlockchainType.Tron

    private fun getEvmAdapter(token: Token, adapterManager: IAdapterManager): Eip20Adapter =
        adapterManager.getAdapterForToken<Eip20Adapter>(token)
            ?: unsupportedToken(token)

    private fun getTronAdapter(token: Token, adapterManager: IAdapterManager): Trc20Adapter =
        adapterManager.getAdapterForToken<Trc20Adapter>(token)
            ?: unsupportedToken(token)

    private fun unsupportedToken(token: Token): Nothing =
        throw UnsupportedException("Unsupported allowance token: ${token.blockchainType} ${token.type}")
}
