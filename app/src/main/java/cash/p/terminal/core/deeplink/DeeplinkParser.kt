package cash.p.terminal.core.deeplink

import android.net.Uri
import cash.p.terminal.R
import cash.p.terminal.core.ICoinManager
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

            else -> null
        }
    }

    /**
     * True for a pcash://auth mini-app connect link. The connect flow is hidden until SWAP6,
     * so scanned auth links must be swallowed instead of surfaced to the caller: they carry a
     * JWT that must never leak into unrelated features as generic scanner text.
     */
    fun isHiddenAuthLink(uri: Uri): Boolean =
        uri.scheme == "pcash" && uri.host == "auth"

    fun parse(text: String): DeeplinkPage? {
        return try {
            parse(Uri.parse(text))
        } catch (e: Exception) {
            null
        }
    }
}
