package cash.p.terminal.modules.qrscanner

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.premiumAction
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.components.ButtonPrimary
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefaultWithIcon
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefaults
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.theme.Dark
import cash.p.terminal.ui_compose.theme.SteelLight
import cash.p.terminal.ui_compose.theme.YellowD
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.journeyapps.barcodescanner.CompoundBarcodeView
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRScannerScreen(
    uiState: QRScannerUiState,
    navController: NavController,
    showPasteButton: Boolean,
    onScan: (String) -> Unit,
    onCloseClick: () -> Unit,
    onCameraPermissionSettingsClick: () -> Unit,
    onGalleryImagePicked: (Uri) -> Unit,
    onErrorMessageConsumed: () -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var showPermissionNeededDialog by remember { mutableStateOf(cameraPermissionState.status != PermissionStatus.Granted) }
    val view = LocalView.current

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(onGalleryImagePicked)
        }

    if (showPermissionNeededDialog) {
        PermissionNeededDialog(
            onOkClick = {
                cameraPermissionState.launchPermissionRequest()
                showPermissionNeededDialog = false
            },
            onCancelClick = {
                showPermissionNeededDialog = false
            }
        )
    }

    LaunchedEffect(uiState.errorMessageRes) {
        uiState.errorMessageRes?.let { errorRes ->
            HudHelper.showErrorMessage(view, errorRes)
            onErrorMessageConsumed()
        }
    }

    ComposeAppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets)
                .background(color = ComposeAppTheme.colors.tyler),
            contentAlignment = Alignment.Center
        ) {
            if (cameraPermissionState.status == PermissionStatus.Granted) {
                ScannerView(onScan)
            } else {
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .background(color = ComposeAppTheme.colors.dark)
                )
                GoToSettingsBox(onCameraPermissionSettingsClick)
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showPasteButton) {
                    ButtonPrimaryYellow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        title = stringResource(R.string.Send_Button_Paste),
                        enabled = !uiState.isDecodingFromImage,
                        onClick = {
                            onScan(TextHelper.getCopiedText())
                        }
                    )
                }

                ButtonPrimaryDefaultWithIcon(
                    icon = R.drawable.star_filled_yellow_16,
                    iconTint = YellowD,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    title = stringResource(R.string.choose_from_photos),
                    enabled = !uiState.isDecodingFromImage,
                    onClick = {
                        navController.premiumAction {
                            galleryLauncher.launch(GALLERY_MIME_TYPE)
                        }
                    }
                )

                ButtonPrimary(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    enabled = !uiState.isDecodingFromImage,
                    content = {
                        Text(
                            text = stringResource(R.string.Button_Cancel),
                            maxLines = 1,
                            color = Dark,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    buttonColors = ButtonPrimaryDefaults.textButtonColors(
                        backgroundColor = SteelLight,
                        contentColor = ComposeAppTheme.colors.dark,
                        disabledBackgroundColor = ComposeAppTheme.colors.steel20,
                        disabledContentColor = ComposeAppTheme.colors.grey50,
                    ),
                    onClick = onCloseClick
                )
                Spacer(Modifier.height(48.dp))
            }

            if (uiState.isDecodingFromImage) {
                LoadingOverlay()
            }
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.tyler.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material.CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = ComposeAppTheme.colors.leah
        )
    }
}

@Composable
private fun ScannerView(onScan: (String) -> Unit) {
    val context = LocalContext.current
    val latestOnScan = rememberUpdatedState(onScan)
    val scanIntent = remember(context) {
        ScanOptions()
            .setOrientationLocked(true)
            .setPrompt("")
            .setBeepEnabled(false)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .createScanIntent(context)
    }
    val barcodeView = remember(scanIntent) {
        CompoundBarcodeView(context).apply {
            initializeFromIntent(scanIntent)
            setStatusText("")
            decodeSingle { result ->
                result.text?.let { decoded ->
                    latestOnScan.value(decoded)
                }
            }
        }
    }
    AndroidView(factory = { barcodeView })
    LifecycleResumeEffect(barcodeView) {
        barcodeView.resume()

        onPauseOrDispose {
            barcodeView.pause()
        }
    }
}

@Composable
private fun GoToSettingsBox(onCameraPermissionSettingsClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        subhead2_grey(
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
            text = stringResource(R.string.ScanQr_CameraPermissionDeniedText)
        )
        Spacer(Modifier.height(24.dp))
        TextPrimaryButton(
            onClick = onCameraPermissionSettingsClick,
            title = stringResource(R.string.ScanQr_GoToSettings)
        )
    }
}

@Composable
private fun TextPrimaryButton(
    title: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        color = ComposeAppTheme.colors.transparent,
        contentColor = SteelLight,
    ) {
        Row(
            Modifier
                .defaultMinSize(
                    minWidth = ButtonPrimaryDefaults.MinWidth,
                    minHeight = ButtonPrimaryDefaults.MinHeight
                )
                .padding(ButtonPrimaryDefaults.ContentPadding)
                .clickable(
                    onClick = onClick,
                    interactionSource = interactionSource,
                    indication = null
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ComposeAppTheme.typography.headline2
                )
            }
        )
    }
}

@Composable
private fun PermissionNeededDialog(
    onOkClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    ComposeAppTheme {
        Dialog(onDismissRequest = onCancelClick) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(color = ComposeAppTheme.colors.lawrence)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                title3_leah(text = stringResource(R.string.ScanQr_CameraPermission_Title))
                Spacer(Modifier.height(12.dp))
                body_leah(text = stringResource(R.string.ScanQr_PleaseGrantCameraPermission))
                Spacer(Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ButtonPrimaryTransparent(
                        onClick = onCancelClick,
                        title = stringResource(R.string.Button_Cancel)
                    )
                    Spacer(Modifier.width(8.dp))
                    ButtonPrimaryYellow(
                        onClick = onOkClick,
                        title = stringResource(R.string.Button_Ok)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PermissionNeededDialogPreview() {
    ComposeAppTheme {
        PermissionNeededDialog({}, {})
    }
}

private const val GALLERY_MIME_TYPE = "image/*"
