package cash.p.terminal.network.quickex.api

object QuickexHelper {
    const val QUICKEX_URL = "quickex.io"
    fun getViewTransactionUrl(orderId: String, destinationAddress: String): String {
        return "https://quickex.io/order/$orderId?destinationAddress=$destinationAddress"
    }
}