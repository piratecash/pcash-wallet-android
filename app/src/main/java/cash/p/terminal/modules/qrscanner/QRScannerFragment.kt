package cash.p.terminal.modules.qrscanner

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.navigation.NavController
import cash.p.terminal.core.deeplink.DeeplinkParser
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.core.managers.isTonConnectDeeplink
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.navigation.QrScannerResult
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.setNavigationResultX
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.R
import cash.p.terminal.tangem.domain.sdk.CardSdkConfigRepository
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.getInput
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.core.net.toUri

class QRScannerFragment : BaseComposeFragment() {

    private val viewModel: QRScannerViewModel by viewModel()
    private val deeplinkParser: DeeplinkParser by inject()
    private val tonConnectManager: TonConnectManager by inject()
    private val cardSdkConfigRepository: CardSdkConfigRepository by inject()

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
            onCloseClick = { navController.popBackStackSafely() },
            onCameraPermissionSettingsClick = ::openCameraPermissionSettings,
            onGalleryImagePicked = viewModel::onImagePicked,
            onErrorMessageConsumed = viewModel::onErrorMessageConsumed
        )
    }

    private fun handleScanResult(decoded: String, navController: NavController) {
        // A scan result can arrive while the fragment is leaving the foreground (e.g. the user
        // backgrounds the app the moment a code is detected, or a decoded image is delivered late).
        // Running pop/navigate then enqueues a fragment transaction that commits during onPause,
        // crashing the FragmentNavigator. Defer navigation until RESUMED instead of dropping it.
        lifecycleScope.launch {
            lifecycle.withResumed {
                navigateForScanResult(decoded, navController)
            }
        }
    }

    private fun navigateForScanResult(decoded: String, navController: NavController) {
        val uri = decoded.toUri()
        if (uri.isTonConnectDeeplink()) {
            // TonConnectManager.handle emits to dappRequestFlow, which MainActivity
            // observes and opens tcNewFragment on this nav controller.
            // Launch on the activity scope so the suspending network call survives
            // this fragment being popped.
            requireActivity().lifecycleScope.launch {
                tonConnectManager.handle(decoded, closeAppOnResult = false)
            }
            navController.popBackStack()
            return
        }

        val deeplinkPage = deeplinkParser.parse(decoded)

        when {
            deeplinkPage != null -> {
                // Pop QR scanner first, then navigate to deeplink destination
                navController.popBackStack()
                if (deeplinkPage.navigationId == R.id.connectMiniAppFragment) {
                    navController.slideFromBottom(deeplinkPage.navigationId, deeplinkPage.input)
                } else {
                    navController.slideFromRight(deeplinkPage.navigationId, deeplinkPage.input)
                }
            }

            deeplinkParser.isHiddenAuthLink(uri) -> {
                // pcash://auth carries a mini-app connect JWT; that flow is hidden until SWAP6.
                // Swallow it so the credential never reaches the caller as generic scanner text.
                navController.popBackStack()
            }

            else -> {
                navController.setNavigationResultX(QrScannerResult(decoded))
                navController.popBackStack()
            }
        }
    }

    private fun openCameraPermissionSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Using the camera can silently disable NFC reader mode outside the Activity lifecycle on
        // some devices (e.g. Samsung), leaving a Tangem card unreadable on the next sign. Restore it.
        cardSdkConfigRepository.forceEnableReaderMode()
    }
}
