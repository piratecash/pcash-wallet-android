package cash.p.terminal.network.github.data

/**
 * Base URLs for the GitHub update feature, supplied by the app layer (from BuildConfig).
 * The proxy URLs are p.cash transparent proxies used as a fallback when GitHub is unreachable.
 */
data class GithubApiConfig(
    val apiBaseUrl: String,
    val rawBaseUrl: String,
    val apiProxyBaseUrl: String,
    val rawProxyBaseUrl: String,
)
