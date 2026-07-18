package cash.p.terminal.core.managers

import cash.p.terminal.core.tryOrNull
import io.horizontalsystems.core.entities.BlockchainType
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import java.net.InetAddress

/**
 * Per-call OkHttp observer that records EVM transport failures (SSL/DNS/timeout/connect) into the
 * shared [NetworkErrorTracker] for one (blockchainType, accountId). Passive: it only reads, never
 * throws into OkHttp, and does not record benign lifecycle-canceled calls. HTTP status errors
 * (4xx/5xx) are successful calls and are intentionally not captured here.
 */
class EvmNetworkErrorEventListener(
    private val blockchainType: BlockchainType,
    private val accountId: String,
    private val tracker: NetworkErrorTracker,
) : EventListener() {

    private var resolvedIps: List<String> = emptyList()

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        resolvedIps = tryOrNull { inetAddressList.mapNotNull { it.hostAddress }.distinct() }.orEmpty()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        // Skip only genuine cancellations (lifecycle stop / unlink / background). Do NOT filter by
        // InterruptedIOException type — SocketTimeoutException is one and timeouts are in scope.
        if (call.isCanceled()) return

        tryOrNull {
            val request = call.request()
            val url = request.url
            // URL is sanitized inside the tracker before it is stored/logged/reported.
            tracker.record(
                blockchainType,
                accountId,
                NetworkErrorInfo(
                    source = url.host,
                    method = request.method,
                    url = url.toString(),
                    host = url.host,
                    resolvedIps = resolvedIps,
                    throwable = ioe,
                )
            )
        }
    }

    class Factory(
        private val blockchainType: BlockchainType,
        private val accountId: String,
        private val tracker: NetworkErrorTracker,
    ) : EventListener.Factory {
        override fun create(call: Call): EventListener =
            EvmNetworkErrorEventListener(blockchainType, accountId, tracker)
    }
}
