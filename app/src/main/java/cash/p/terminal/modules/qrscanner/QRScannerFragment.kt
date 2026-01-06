package cash.p.terminal.modules.qrscanner

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
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
        // Cache input to survive configuration changes and returning from gallery picker
        val input: Input = rememberSaveable {
            navController.getInput<Input>() ?: Input("")
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.scanResult.collectLatest { decoded ->
                navController.setNavigationResultX(Result(decoded))
                navController.popBackStack()
            }
        }

        QRScannerScreen(
            uiState = uiState,
            title = input.title,
            navController = navController,
            showPasteButton = input.showPasteButton,
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
    data class Input(val title: String, val showPasteButton: Boolean = false) : Parcelable

    @Parcelize
    data class Result(val text: String) : Parcelable
}
