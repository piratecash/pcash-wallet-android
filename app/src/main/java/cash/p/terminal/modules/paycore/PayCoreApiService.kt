package cash.p.terminal.modules.paycore

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.network.data.setJsonBody
import cash.p.terminal.wallet.Account
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PayCoreApiService(
    private val httpClient: HttpClient,
    private val signatureHelper: PayCoreSignatureHelper,
    private val dispatcherProvider: DispatcherProvider
) {
    private companion object {
        const val BASE_URL = "https://pirate.paycore.pw/"
    }

    private fun HttpRequestBuilder.signedHeaders(
        url: String,
        networkType: String,
        signingAccount: Account? = null
    ) {
        val headers = if (signingAccount == null) {
            signatureHelper.getSignedHeaders(url, networkType)
        } else {
            signatureHelper.getSignedHeaders(url, networkType, signingAccount)
        }
        headers.forEach { (key, value) -> header(key, value) }
    }

    suspend fun getRate(
        currencyFrom: String,
        currencyTo: String,
        amountFrom: String? = null,
        amountTo: String? = null
    ): PayCoreRateResponse = withContext(dispatcherProvider.io) {
        val queryParams = mutableListOf(
            "currency_from" to currencyFrom,
            "currency_to" to currencyTo
        )
        amountFrom?.let { queryParams.add("amount_from" to it) }
        amountTo?.let { queryParams.add("amount_to" to it) }

        val queryString = queryParams.joinToString("&") { "${it.first}=${it.second}" }
        val url = "${BASE_URL}api/v2/wallet/rate?$queryString"
        val networkType = networkTypeForRate(currencyFrom, currencyTo)

        val response = httpClient.get(url) {
            signedHeaders(url, networkType)
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            if (response.status.value == 400 && errorBody.indicatesPayCoreAmountOutOfRange()) {
                throw PayCoreAmountOutOfRangeException(errorBody.payCoreErrorMessage().orEmpty())
            }
            error("HTTP ${response.status.value}: ${response.status.description}${errorBody.payCoreErrorMessageSuffix()}")
        }
        response.body()
    }

    suspend fun createWallet(
        request: PayCoreWalletCreateRequest,
        signingNetworkType: String = request.networkType
    ): PayCoreWalletCreateResponse = withContext(dispatcherProvider.io) {
        val url = BASE_URL + "api/v2/wallet/create"
        httpClient.post(url) {
            signedHeaders(url, signingNetworkType)
            setJsonBody(request)
        }.parseBody()
    }

    suspend fun changeWallet(
        request: PayCoreWalletChangeRequest,
        signingNetworkType: String = request.networkType,
        signingAccount: Account? = null
    ) = withContext(dispatcherProvider.io) {
        val url = BASE_URL + "api/v2/wallet/change"
        httpClient.post(url) {
            signedHeaders(url, signingNetworkType, signingAccount)
            setJsonBody(request)
        }.requireSuccess()
    }

    suspend fun getPayoutAddress(request: PayCorePayoutAddressRequest): PayCorePayoutAddressResponse = withContext(dispatcherProvider.io) {
        val url = BASE_URL + "api/v2/wallet/payout/address"
        httpClient.post(url) {
            signedHeaders(url, request.networkType)
            setJsonBody(request)
        }.parseBody()
    }

    suspend fun processPayOut(
        request: PayCorePayoutProcessRequest,
        networkType: String
    ): PayCorePayoutProcessResponse = withContext(dispatcherProvider.io) {
        val url = BASE_URL + "api/v2/wallet/payout/process"
        httpClient.post(url) {
            signedHeaders(url, networkType)
            setJsonBody(request)
        }.parseBody()
    }

    suspend fun createPayment(request: PayCorePaymentCreateRequest): PayCorePaymentCreateResponse = withContext(dispatcherProvider.io) {
        val url = BASE_URL + "api/v2/wallet/payment/create"
        httpClient.post(url) {
            signedHeaders(url, request.networkType)
            setJsonBody(request)
        }.parseBody()
    }

    suspend fun getTransactionStatus(
        transactionType: String,
        transactionId: String,
        networkType: String
    ): PayCoreTransactionStatusResponse = withContext(dispatcherProvider.io) {
        val url = "${BASE_URL}api/v2/wallet/status?transaction_type=$transactionType&transaction_id=$transactionId"
        httpClient.get(url) {
            signedHeaders(url, networkType)
        }.parseBody()
    }

    private fun networkTypeForRate(currencyFrom: String, currencyTo: String): String {
        return requireNotNull(PayCoreNetworkMapper.fromCurrencies(currencyFrom, currencyTo)) {
            "Unsupported PayCore rate pair: $currencyFrom -> $currencyTo"
        }
    }
}

private suspend fun HttpResponse.requireSuccess() {
    if (!status.isSuccess()) {
        error("HTTP ${status.value}: ${status.description}${bodyAsText().payCoreErrorMessageSuffix()}")
    }
}

private suspend inline fun <reified T : Any> HttpResponse.parseBody(): T {
    requireSuccess()
    return body()
}

private val payCoreJson = Json { ignoreUnknownKeys = true }

internal fun String.payCoreErrorMessage(): String? {
    val errorBody = trim()
    if (errorBody.isEmpty()) return null
    return tryOrNull {
        payCoreJson.decodeFromString<PayCoreErrorResponse>(errorBody).error
    }?.takeIf { it.isNotBlank() } ?: errorBody
}

internal fun String.payCoreErrorMessageSuffix(): String {
    val message = payCoreErrorMessage() ?: return ""
    return " - $message"
}

internal fun String.indicatesPayCoreAmountOutOfRange(): Boolean {
    val message = payCoreErrorMessage().orEmpty().lowercase()
    if (message.isEmpty()) return false
    val mentionsAmount = "amount" in message
    val mentionsLimit = "limit" in message || "minimum" in message || "maximum" in message
    return mentionsAmount && mentionsLimit
}

internal class PayCoreAmountOutOfRangeException(message: String) : RuntimeException(message)

@Serializable
private data class PayCoreErrorResponse(val error: String)
