package cash.p.terminal.network.quickex.domain.repository

import cash.p.terminal.network.quickex.data.entity.request.NewTransactionQuickexRequest
import cash.p.terminal.network.quickex.domain.entity.NewTransactionQuickexResponse
import cash.p.terminal.network.quickex.domain.entity.QuickexInstrument
import cash.p.terminal.network.quickex.domain.entity.QuickexRates
import java.math.BigDecimal

interface QuickexRepository {
    suspend fun getAvailablePairs(): List<QuickexInstrument>

    suspend fun getRates(
        fromCurrency: String,
        fromNetwork: String,
        toCurrency: String,
        toNetwork: String,
        claimedDepositAmount: BigDecimal
    ): QuickexRates

    suspend fun createTransaction(
        newTransactionRequest: NewTransactionQuickexRequest
    ): NewTransactionQuickexResponse
}