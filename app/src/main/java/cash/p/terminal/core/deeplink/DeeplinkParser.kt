package cash.p.terminal.core.deeplink

import android.net.Uri
import cash.p.terminal.R
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppDeeplinkInput
import cash.p.terminal.modules.main.DeeplinkPage
import cash.p.terminal.modules.multiswap.SwapDeeplinkInput
import cash.p.terminal.wallet.entities.TokenQuery

/**
 * Parses pcash:// deeplinks for swap and premium screens.
 * Used by both QRScannerFragment (for scanned QR codes) and MainViewModel (for external deeplinks).
 */
class DeeplinkParser(
    private val coinManager: ICoinManager
) {
    fun parse(uri: Uri): DeeplinkPage? {
        if (uri.scheme != "pcash") {
            return null
        }

        return when (uri.host) {
            "premium" -> {
                DeeplinkPage(R.id.aboutPremiumFragment, null)
            }

            "swap" -> {
                val toTokenParam = uri.getQueryParameter("to_token")
                val tokenQuery = when (toTokenParam?.uppercase()) {
                    "PIRATE" -> TokenQuery.PirateCashBnb
                    "COSA" -> TokenQuery.CosantaBnb
                    else -> null
                }
                val token = tokenQuery?.let { coinManager.getToken(it) }
                DeeplinkPage(R.id.multiswap, SwapDeeplinkInput(token))
            }

            "auth" -> {
                val jwt = uri.getQueryParameter("token") ?: return null
                val endpoint = when (uri.getQueryParameter("env")) {
                    "stage" -> "https://anubis.pirate.place/"
                    "dev" -> "https://cash.p.cash/"
                    else -> "https://p.cash/"
                }
                DeeplinkPage(
                    R.id.connectMiniAppFragment, ConnectMiniAppDeeplinkInput(
                        jwt = jwt,
                        endpoint = endpoint
                    )
                )
            }

            else -> null
        }
    }

    fun parse(text: String): DeeplinkPage? {
        return try {
            parse(Uri.parse(text))
        } catch (e: Exception) {
            null
        }
    }
}
