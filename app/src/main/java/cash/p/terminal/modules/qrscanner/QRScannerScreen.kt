package cash.p.terminal.modules.qrscanner

import android.Manifest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import java.util.concurrent.Executors
import androidx.compose.ui.tooling.preview.Preview as ComposePreview

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
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val hasScanned = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setHighResolutionDisabled(false)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor, QrCodeAnalyzer { result ->
                        if (hasScanned.compareAndSet(false, true)) {
                            mainHandler.post {
                                onScan(result)
                            }
                        }
                    })
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (_: Exception) {
                // Camera binding failed
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        ScannerOverlay()
    }
}

@Composable
private fun ScannerOverlay() {
    val density = LocalDensity.current
    val scanWindowSize = with(density) { 250.dp.toPx() }
    val cornerLength = with(density) { 30.dp.toPx() }
    val strokeWidth = with(density) { 4.dp.toPx() }
    val cornerColor = ComposeAppTheme.colors.yellowD

    // Animated scan line
    val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLinePosition"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Calculate scan window position (centered)
        val scanLeft = (canvasWidth - scanWindowSize) / 2
        val scanTop = (canvasHeight - scanWindowSize) / 2
        val scanRight = scanLeft + scanWindowSize
        val scanBottom = scanTop + scanWindowSize

        val overlayColor = Color.Black.copy(alpha = 0.6f)

        // Create cutout path for the scan window
        val cutoutPath = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(scanLeft, scanTop, scanRight, scanBottom))
        }

        // Draw overlay with cutout using clipPath with Difference operation
        clipPath(cutoutPath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
            drawRect(color = overlayColor)
        }

        // Draw corner brackets (orange) - L-shapes with rounded corners
        val halfStroke = strokeWidth / 2

        // Top-left corner bracket
        val topLeftPath = Path().apply {
            moveTo(scanLeft + halfStroke, scanTop + cornerLength)
            lineTo(scanLeft + halfStroke, scanTop + halfStroke)
            quadraticTo(
                scanLeft + halfStroke, scanTop + halfStroke,
                scanLeft + halfStroke, scanTop + halfStroke
            )
            lineTo(scanLeft + cornerLength, scanTop + halfStroke)
        }
        drawPath(topLeftPath, cornerColor, style = Stroke(width = strokeWidth))

        // Top-right corner bracket
        val topRightPath = Path().apply {
            moveTo(scanRight - cornerLength, scanTop + halfStroke)
            lineTo(scanRight - halfStroke, scanTop + halfStroke)
            quadraticTo(
                scanRight - halfStroke, scanTop + halfStroke,
                scanRight - halfStroke, scanTop + halfStroke
            )
            lineTo(scanRight - halfStroke, scanTop + cornerLength)
        }
        drawPath(topRightPath, cornerColor, style = Stroke(width = strokeWidth))

        // Bottom-left corner bracket
        val bottomLeftPath = Path().apply {
            moveTo(scanLeft + halfStroke, scanBottom - cornerLength)
            lineTo(scanLeft + halfStroke, scanBottom - halfStroke)
            quadraticTo(
                scanLeft + halfStroke, scanBottom - halfStroke,
                scanLeft + halfStroke, scanBottom - halfStroke
            )
            lineTo(scanLeft + cornerLength, scanBottom - halfStroke)
        }
        drawPath(bottomLeftPath, cornerColor, style = Stroke(width = strokeWidth))

        // Bottom-right corner bracket
        val bottomRightPath = Path().apply {
            moveTo(scanRight - cornerLength, scanBottom - halfStroke)
            lineTo(scanRight - halfStroke, scanBottom - halfStroke)
            quadraticTo(
                scanRight - halfStroke, scanBottom - halfStroke,
                scanRight - halfStroke, scanBottom - halfStroke
            )
            lineTo(scanRight - halfStroke, scanBottom - cornerLength)
        }
        drawPath(bottomRightPath, cornerColor, style = Stroke(width = strokeWidth))

        // Draw animated red scan line
        val padding = 16.dp.toPx()
        val lineY = scanTop + padding + (scanWindowSize - 2 * padding) * scanLinePosition
        drawLine(
            color = Color.Red,
            start = Offset(scanLeft + padding, lineY),
            end = Offset(scanRight - padding, lineY),
            strokeWidth = 2.dp.toPx()
        )
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

@ComposePreview
@Composable
private fun PermissionNeededDialogPreview() {
    ComposeAppTheme {
        PermissionNeededDialog({}, {})
    }
}

private const val GALLERY_MIME_TYPE = "image/*"
