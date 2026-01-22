package cash.p.terminal.modules.qrscanner

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import cash.p.terminal.core.deeplink.DeeplinkParser
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.navigation.QrScannerResult
import cash.p.terminal.navigation.setNavigationResultX
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.getInput
import kotlinx.coroutines.flow.collectLatest
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class QRScannerFragment : BaseComposeFragment() {

    private val viewModel: QRScannerViewModel by viewModel()
    private val deeplinkParser: DeeplinkParser by inject()

    @Composable
    override fun GetContent(navController: NavController) {
        // Cache input to survive configuration changes and returning from gallery picker
        val input: QrScannerInput = rememberSaveable {
            navController.getInput<QrScannerInput>() ?: QrScannerInput("")
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.scanResult.collectLatest { decoded ->
                handleScanResult(decoded, navController)
            }
        }

        QRScannerScreen(
            uiState = uiState,
            title = input.title,
            navController = navController,
            showPasteButton = input.showPasteButton,
            allowGalleryWithoutPremium = input.allowGalleryWithoutPremium,
            onScan = { decoded ->
                handleScanResult(decoded, navController)
            },
            onCloseClick = navController::popBackStack,
            onCameraPermissionSettingsClick = ::openCameraPermissionSettings,
            onGalleryImagePicked = viewModel::onImagePicked,
            onErrorMessageConsumed = viewModel::onErrorMessageConsumed
        )
    }

    private fun handleScanResult(decoded: String, navController: NavController) {
        val deeplinkPage = deeplinkParser.parse(decoded)

        if (deeplinkPage != null) {
            // Pop QR scanner first, then navigate to deeplink destination
            navController.popBackStack()
            navController.slideFromRight(deeplinkPage.navigationId, deeplinkPage.input)
        } else {
            // Return result to caller as before
            navController.setNavigationResultX(QrScannerResult(decoded))
            navController.popBackStack()
        }
    }

    private fun openCameraPermissionSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }
}
