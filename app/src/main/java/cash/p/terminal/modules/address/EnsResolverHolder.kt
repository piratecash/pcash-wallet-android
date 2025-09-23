package cash.p.terminal.modules.address

import cash.p.terminal.core.providers.AppConfigProvider
import org.web3j.ens.EnsResolver
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

object EnsResolverHolder {
    val resolver by lazy {
        val okHttpClient = HttpService.getOkHttpClientBuilder().build()
        val httpService = HttpService(AppConfigProvider.blocksDecodedEthereumRpc, okHttpClient)
        val web3j = Web3j.build(httpService)

        EnsResolver(web3j)
    }
}
