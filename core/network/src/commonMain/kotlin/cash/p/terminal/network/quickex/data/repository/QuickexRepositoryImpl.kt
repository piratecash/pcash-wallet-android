package cash.p.terminal.network.quickex.data.repository

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.quickex.api.QuickexApi
import cash.p.terminal.network.quickex.data.entity.request.NewTransactionQuickexRequest
import cash.p.terminal.network.quickex.data.mapper.QuickexMapper
import cash.p.terminal.network.quickex.domain.entity.OrderEventKind
import cash.p.terminal.network.quickex.domain.repository.QuickexRepository
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

internal class QuickexRepositoryImpl(
    private val quickexApi: QuickexApi,
    private val quickexMapper: QuickexMapper
) : QuickexRepository, SwapProviderTransactionStatusRepository {
    override suspend fun getAvailablePairs() = withContext(Dispatchers.IO) {
        quickexApi.getAvailableCurrencies().let(quickexMapper::mapCurrencyDtoToCurrency)
    }

    override suspend fun getRates(
        fromCurrency: String,
        fromNetwork: String,
        toCurrency: String,
        toNetwork: String,
        claimedDepositAmount: BigDecimal,
    ) = withContext(Dispatchers.IO) {
        quickexApi.getRates(
            fromCurrency = fromCurrency,
            fromNetwork = fromNetwork,
            toCurrency = toCurrency,
            toNetwork = toNetwork,
            claimedDepositAmount = claimedDepositAmount,
        ).let(quickexMapper::mapRatesDto)
    }

    override suspend fun createTransaction(
        newTransactionRequest: NewTransactionQuickexRequest
    ) = withContext(Dispatchers.IO) {
        quickexApi.createTransaction(newTransactionRequest)
            .let(quickexMapper::mapNewTransactionQuickexResponseDto)
    }

    private suspend fun getTransactionStatus(
        destinationAddress: String,
        orderId: Long
    ) = withContext(Dispatchers.IO) {
        quickexApi.getTransactionStatus(destinationAddress, orderId)
            .let(quickexMapper::mapTransactionQuickexStatusDto)
    }

    override suspend fun getTransactionStatus(
        transactionId: String,
        destinationAddress: String
    ): TransactionStatusEnum {
        val transactionStatus = getTransactionStatus(
            destinationAddress,
            transactionId.toLong()
        )
        return if (transactionStatus.completed) {
            TransactionStatusEnum.FINISHED
        } else {
            when(transactionStatus.orderEvents.firstOrNull()?.kind) {
                OrderEventKind.CREATION_END -> TransactionStatusEnum.WAITING
                OrderEventKind.INCOMING_FUNDS_DETECTED -> TransactionStatusEnum.CONFIRMING
                OrderEventKind.DEPOSIT_REGISTERED -> TransactionStatusEnum.CONFIRMING
                OrderEventKind.FUNDS_WITHDRAWAL_START -> TransactionStatusEnum.SENDING
                OrderEventKind.WITHDRAWAL_COMPLETED -> TransactionStatusEnum.FINISHED
                OrderEventKind.AMLBOT_AML_FROZEN_BY_LIQUIDITY_PROVIDER -> TransactionStatusEnum.FAILED
                OrderEventKind.REFUND_REQUESTED -> TransactionStatusEnum.WAITING
                OrderEventKind.REFUND_COMPLETED -> TransactionStatusEnum.REFUNDED
                null -> TransactionStatusEnum.NEW
            }
        }
    }
}