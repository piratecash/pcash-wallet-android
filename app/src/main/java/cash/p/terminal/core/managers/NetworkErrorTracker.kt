package cash.p.terminal.core.managers

import cash.p.terminal.modules.blockchainstatus.logTag
import io.horizontalsystems.bitcoincore.network.BitcoinNetworkError
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.extractCertificateChainInfo
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.solanakit.network.SolanaNetworkError
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Blockchain-agnostic normalized network error. Kit-specific error types
 * (BitcoinNetworkError, SolanaNetworkError, …) map onto this at the app boundary.
 * `method` is an opaque operation label (HTTP verb for Bitcoin, RPC method for Solana).
 */
data class NetworkErrorInfo(
    val source: String,
    val method: String,
    val url: String,
    val host: String,
    val resolvedIps: List<String>,
    val throwable: Throwable
)

/**
 * Shared store for the most recent network (SSL/HTTP) error per blockchain+account.
 * Feeds AppStatus (via adapter statusInfo) and the "Report a problem" diagnostics.
 * Generalization of the former Solana-only handling in SolanaKitManager.
 */
class NetworkErrorTracker {

    private val recentByKey = ConcurrentHashMap<String, Map<String, String>>()

    fun record(blockchainType: BlockchainType, accountId: String, error: NetworkErrorInfo) {
        val info = buildInfo(error)
        recentByKey[key(blockchainType, accountId)] = info

        val message = info.entries.joinToString(separator = "\n") { (key, value) -> "$key: $value" }
        // Use logTag (not uid): the blockchain status screen filters its APP LOG by
        // BlockchainType.logTag (e.g. "BTC"/"LTC"), so logging under uid would hide the entry there.
        AppLogger(blockchainType.logTag).getScoped("network").warning(message, error.throwable)
        Timber.tag("NetworkError").e(error.throwable, message)
    }

    fun errorInfo(blockchainType: BlockchainType, accountId: String): Map<String, String>? =
        recentByKey[key(blockchainType, accountId)]

    private fun buildInfo(error: NetworkErrorInfo): Map<String, String> {
        val info = linkedMapOf(
            "Recent Network Error Source" to sanitizeNetworkUrl(error.source),
            "Recent Network Error Method" to error.method,
            "Recent Network Error URL" to sanitizeNetworkUrl(error.url),
            "Recent Network Error Host" to error.host,
            "Recent Network Error Type" to error.throwable.javaClass.simpleName,
            "Recent Network Error Message" to error.throwable.message.orEmpty(),
        ).filterValues(String::isNotBlank).toMutableMap()

        if (error.resolvedIps.isNotEmpty()) {
            info["Recent Network Error Resolved IPs"] = error.resolvedIps.joinToString(", ")
        }

        info += error.throwable.extractCertificateChainInfo()
        return info
    }

    private fun key(blockchainType: BlockchainType, accountId: String) = "${blockchainType.uid}:$accountId"
}

fun SolanaNetworkError.toNetworkErrorInfo() =
    NetworkErrorInfo(source, method, url, host, resolvedIps, throwable)

fun BitcoinNetworkError.toNetworkErrorInfo() =
    NetworkErrorInfo(source, method, url, host, resolvedIps, throwable)

private const val REDACTED = "redacted"
private val URL_USERINFO = Regex("://[^/@\\s]*@")
private val SECRET_QUERY_PARAM =
    Regex("([?&](?:apikey|api_key|apiKey|key|token|secret|access_token)=)[^&#\\s]*", RegexOption.IGNORE_CASE)

// A long opaque path segment (e.g. Infura/Alchemy key in the path) — redact it.
private val SECRET_PATH_SEGMENT = Regex("/[A-Za-z0-9_-]{20,}")

// The segment following a version prefix (`/v2/<key>`, `/v3/<key>`) is redacted regardless of
// length — short custom-RPC keys would slip past the long-token rule otherwise.
private val VERSION_PREFIXED_SEGMENT = Regex("/(v\\d+)/[^/?#\\s]+", RegexOption.IGNORE_CASE)

/**
 * Redacts credentials that can appear in network URLs before they are stored/logged/reported:
 * userinfo (`user:pass@`), secret query params (apikey/token/…), long opaque path segments, and the
 * segment after a version prefix (Infura `/v3/<key>`, Alchemy `/v2/<key>`, including short keys).
 * String-based (no parse) so a malformed URL never leaks.
 */
fun sanitizeNetworkUrl(url: String): String =
    url
        .replace(URL_USERINFO, "://")
        .replace(SECRET_QUERY_PARAM) { "${it.groupValues[1]}$REDACTED" }
        .replace(SECRET_PATH_SEGMENT, "/$REDACTED")
        .replace(VERSION_PREFIXED_SEGMENT) { "/${it.groupValues[1]}/$REDACTED" }
