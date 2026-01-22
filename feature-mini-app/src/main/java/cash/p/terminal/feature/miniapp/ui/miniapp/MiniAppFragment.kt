package cash.p.terminal.feature.miniapp.ui.miniapp

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.NavController
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.navigation.openQrScanner
import cash.p.terminal.ui_compose.BaseComposeFragment
import org.koin.androidx.viewmodel.ext.android.viewModel

class MiniAppFragment : BaseComposeFragment() {

    private val viewModel by viewModel<MiniAppViewModel>()

    @Composable
    override fun GetContent(navController: NavController) {
        val context = LocalContext.current

        MiniAppScreen(
            uiState = viewModel.uiState,
            onConnectionClick = {
                navController.openQrScanner(
                    title = context.getString(R.string.mini_app_connection),
                    showPasteButton = true,
                    allowGalleryWithoutPremium = true
                ) { _ -> }
            },
            onStartEarningClick = {
                val intent = Intent(Intent.ACTION_VIEW, TELEGRAM_BOT_URL.toUri())
                context.startActivity(intent)
            },
            onClose = navController::popBackStack
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateConnectionStatus()
    }

    companion object {
        private const val TELEGRAM_BOT_URL = "https://t.me/piratecash_bot?start=mobile"
    }
}
