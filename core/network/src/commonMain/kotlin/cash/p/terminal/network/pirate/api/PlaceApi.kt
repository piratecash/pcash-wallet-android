package cash.p.terminal.network.pirate.api

import cash.p.terminal.network.api.parseResponse
import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.data.entity.ChartPeriod
import cash.p.terminal.network.data.setJsonBody
import cash.p.terminal.network.pirate.data.entity.CalculatorDataDto
import cash.p.terminal.network.pirate.data.entity.ChangeNowAssociatedCoinDto
import cash.p.terminal.network.pirate.data.entity.CoinsPriceChangeRequest
import cash.p.terminal.network.pirate.data.entity.InvestmentDataDto
import cash.p.terminal.network.pirate.data.entity.InvestmentGraphDataDto
import cash.p.terminal.network.pirate.data.entity.MarketTickerDto
import cash.p.terminal.network.pirate.data.entity.PiratePlaceCoinDto
import cash.p.terminal.network.pirate.data.entity.PriceChangeCoinInfoDto
import cash.p.terminal.network.pirate.data.entity.StakeDataDto
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import java.util.Locale

internal class PlaceApi(
    private val httpClient: HttpClient,
    private val appHeadersProvider: AppHeadersProvider
) {
    private companion object {
        const val PIRATE_BASE_PLACE_URL = "https://p.cash/api/"
    }

    private fun HttpRequestBuilder.appHeaders() {
        header("App-Version", appHeadersProvider.appVersion)
        header(HttpHeaders.AcceptLanguage, appHeadersProvider.currentLanguage)
        appHeadersProvider.appSignature?.let { header("App-Signature", it) }
    }

    suspend fun getCoinInfo(coin: String): PiratePlaceCoinDto {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "coins/$coin")
            appHeaders()
        }.parseResponse()
    }

    suspend fun getCoinsPriceChange(
        coins: List<String>,
        currencyCode: String
    ): List<PriceChangeCoinInfoDto> {
        return httpClient.post {
            url(PIRATE_BASE_PLACE_URL + "mobile/coins")
            appHeaders()
            setJsonBody(CoinsPriceChangeRequest(
                uids = coins,
                currency = currencyCode
            ))
        }.parseResponse()
    }

    suspend fun getCoinPriceChart(
        coin: String,
        periodType: ChartPeriod
    ): List<List<String>> {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "coins/$coin/graph/usd/${periodType.value}")
            appHeaders()
        }.parseResponse()
    }

    suspend fun getMarketTickers(
        coin: String
    ): List<MarketTickerDto> {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "coins/$coin/tickers")
            appHeaders()
        }.parseResponse()
    }

    suspend fun getInvestmentData(coin: String, address: String): InvestmentDataDto {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "invest/$coin/$address")
            appHeaders()
        }.parseResponse()
    }

    suspend fun getChangeNowCoinAssociation(uid: String): List<ChangeNowAssociatedCoinDto> {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "changenow/$uid")
            appHeaders()
        }.parseResponse()
    }

    suspend fun getInvestmentChart(
        coin: String,
        address: String,
        period: String
    ): InvestmentGraphDataDto {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "invest/$coin/$address/graph/$period")
            appHeaders()
        }.parseResponse()
    }

    suspend fun getStakeData(coin: String, address: String): StakeDataDto {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "invest/$coin/$address/stake")
            appHeaders()
        }.parseResponse()
    }

    suspend fun getCalculatorData(coin: String, amount: Double): CalculatorDataDto {
        return httpClient.get {
            val formattedAmount = String.format(Locale.US, "%s", amount)
            url(PIRATE_BASE_PLACE_URL + "invest/$coin/calculator")
            parameter("amount", formattedAmount)
            appHeaders()
        }.parseResponse()
    }

    // Premium API methods
    suspend fun checkTrialPremiumStatus(address: String): HttpResponse {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "mobile/premium/$address")
            appHeaders()
        }
    }

    suspend fun activateTrialPremium(address: String): HttpResponse {
        return httpClient.post {
            url(PIRATE_BASE_PLACE_URL + "mobile/premium/$address")
            appHeaders()
        }
    }
}