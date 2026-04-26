package cash.p.terminal.trezor.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cash.p.terminal.trezor.domain.TrezorDeepLinkManager
import org.koin.android.ext.android.inject

class TrezorCallbackActivity : AppCompatActivity() {
    private val deepLinkManager: TrezorDeepLinkManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { uri ->
            val token = uri.getQueryParameter(TrezorDeepLinkManager.PARAM_REQUEST_TOKEN) ?: return@let
            val response = uri.getQueryParameter("response") ?: return@let
            deepLinkManager.onCallbackReceived(token, response)
        }
        finish()
    }
}
