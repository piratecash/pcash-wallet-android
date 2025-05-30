package cash.p.terminal.core.providers

import cash.p.terminal.core.IFeeRateProvider
import io.horizontalsystems.feeratekit.FeeRateKit
import io.horizontalsystems.feeratekit.model.FeeProviderConfig
import io.horizontalsystems.feeratekit.providers.MempoolSpaceProvider
import io.reactivex.Single
import kotlinx.coroutines.rx2.await
import java.math.BigInteger

class FeeRateProvider(appConfig: AppConfigProvider) {

    private val feeRateKit: FeeRateKit by lazy {
        FeeRateKit(
            FeeProviderConfig(
                ethEvmUrl = appConfig.blocksDecodedEthereumRpc,
                ethEvmAuth = null,
                bscEvmUrl = FeeProviderConfig.defaultBscEvmUrl(),
                mempoolSpaceUrl = appConfig.mempoolSpaceUrl
            )
        )
    }

    fun bitcoinFeeRate(): Single<MempoolSpaceProvider.RecommendedFees> {
        return feeRateKit.bitcoin()
    }

    fun litecoinFeeRate(): Single<BigInteger> {
        return feeRateKit.litecoin()
    }

    fun dogecoinFeeRate(): Single<BigInteger> {
        return Single.just(BigInteger("3000"))
    }

    fun bitcoinCashFeeRate(): Single<BigInteger> {
        return feeRateKit.bitcoinCash()
    }

    fun dashFeeRate(): Single<BigInteger> {
        return feeRateKit.dash()
    }

    fun pirateCashFeeRate(): Single<BigInteger> {
        return Single.just(BigInteger("7000"))
    }

}

class BitcoinFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override val feeRateChangeable = true

    override suspend fun getFeeRates(): FeeRates {
        val bitcoinFeeRate = feeRateProvider.bitcoinFeeRate().await()
        return FeeRates(bitcoinFeeRate.halfHourFee, bitcoinFeeRate.minimumFee)
    }
}

class LitecoinFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        val feeRate = feeRateProvider.litecoinFeeRate().await()
        return FeeRates(feeRate.toInt())
    }
}

class DogecoinFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        val feeRate = feeRateProvider.dogecoinFeeRate().await()
        return FeeRates(feeRate.toInt())
    }
}

class BitcoinCashFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        val feeRate = feeRateProvider.bitcoinCashFeeRate().await()
        return FeeRates(feeRate.toInt())
    }
}

class DashFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        val feeRate = feeRateProvider.dashFeeRate().await()
        return FeeRates(feeRate.toInt())
    }
}

class CosantaFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        val feeRate = feeRateProvider.dashFeeRate().await()
        return FeeRates(feeRate.toInt())
    }
}

class PirateCashFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        val feeRate = feeRateProvider.pirateCashFeeRate().await()
        return FeeRates(feeRate.toInt())
    }
}

class ECashFeeRateProvider : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        return FeeRates(1)
    }
}

data class FeeRates(
    val recommended: Int,
    val minimum: Int = 0,
)