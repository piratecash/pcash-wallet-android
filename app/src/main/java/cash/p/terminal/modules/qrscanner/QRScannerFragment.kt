package cash.p.terminal.modules.qrscanner

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import cash.p.terminal.navigation.setNavigationResultX
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.getInput
import kotlinx.coroutines.flow.collectLatest
import kotlinx.parcelize.Parcelize
import org.koin.androidx.viewmodel.ext.android.viewModel

class QRScannerFragment : BaseComposeFragment() {

    private val viewModel: QRScannerViewModel by viewModel()

    @Composable
    override fun GetContent(navController: NavController) {
        val input = navController.getInput<Input>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.scanResult.collectLatest { decoded ->
                navController.setNavigationResultX(Result(decoded))
                navController.popBackStack()
            }
        }

        QRScannerScreen(
            uiState = uiState,
            navController = navController,
            showPasteButton = input?.showPasteButton ?: false,
            onScan = { decoded ->
                navController.setNavigationResultX(Result(decoded))
                navController.popBackStack()
            },
            onCloseClick = navController::popBackStack,
            onCameraPermissionSettingsClick = ::openCameraPermissionSettings,
            onGalleryImagePicked = viewModel::onImagePicked,
            onErrorMessageConsumed = viewModel::onErrorMessageConsumed
        )
    }

    private fun openCameraPermissionSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    @Parcelize
    data class Input(val showPasteButton: Boolean = false) : Parcelable

    @Parcelize
    data class Result(val text: String) : Parcelable
}
