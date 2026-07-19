package cash.p.terminal.core.managers

import cash.p.terminal.core.tryOrNull
import io.horizontalsystems.core.entities.BlockchainType
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress

/**
 * Per-call OkHttp observer that records transport failures (SSL/DNS/timeout/connect) into the
 * shared [NetworkErrorTracker] for one (blockchainType, accountId). Passive: it only reads, never
 * throws into OkHttp, and does not record benign lifecycle-canceled calls. HTTP status errors
 * (4xx/5xx) are successful calls and are intentionally not captured here.
 */
class NetworkErrorEventListener(
    private val blockchainType: BlockchainType,
    private val accountId: String,
    private val tracker: NetworkErrorTracker,
) : EventListener() {

    private var resolvedIps: List<String> = emptyList()

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        resolvedIps = tryOrNull { inetAddressList.mapNotNull { it.hostAddress }.distinct() }.orEmpty()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        // Skip only genuine lifecycle cancellations (stop / unlink / background), which surface as a
        // plain IOException. OkHttp implements callTimeout() by canceling the call, so a call-level
        // timeout also arrives with isCanceled()==true but as an InterruptedIOException — keep those,
        // like SocketTimeoutException, since timeouts are exactly what this listener must diagnose.
        if (call.isCanceled() && ioe !is InterruptedIOException) return

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
            NetworkErrorEventListener(blockchainType, accountId, tracker)
    }
}
