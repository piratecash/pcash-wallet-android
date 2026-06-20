package cash.p.terminal.modules.send.offline

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.ui.compose.components.PcashQrCodeDefaults
import cash.p.terminal.ui.compose.components.PcashQrCodeImage
import cash.p.terminal.ui.compose.components.createPcashQrCodeBitmap
import cash.p.terminal.ui.compose.components.rememberPcashQrCodePainter
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryCircle
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.DefaultDispatcherProvider
import kotlinx.coroutines.launch

@Composable
internal fun OfflineTransactionTransferScreen(
    transaction: OfflineSignedTransaction?,
    selectedFormat: OfflineTransactionFormat,
    qrCodeSaver: OfflineQrCodeSaver,
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val view = LocalView.current
    val currentOnBackClick by rememberUpdatedState(onBackClick)

    LaunchedEffect(transaction) {
        if (transaction == null) {
            HudHelper.showErrorMessage(view, R.string.Error)
            currentOnBackClick()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.offline_transaction_transfer_title),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                },
                menuItems = listOf(),
            )
        },
        bottomBar = {
            if (transaction != null) {
                TransferDoneButton(
                    onDoneClick = onDoneClick,
                    windowInsets = windowInsets,
                )
            }
        },
    ) { paddingValues ->
        when {
            transaction != null -> TransferContent(
                modifier = Modifier.padding(paddingValues),
                transaction = transaction,
                selectedFormat = selectedFormat,
                qrCodeSaver = qrCodeSaver,
            )
        }
    }
}

@Composable
private fun TransferDoneButton(
    onDoneClick: () -> Unit,
    windowInsets: WindowInsets,
) {
    ButtonPrimaryYellow(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        title = stringResource(R.string.Button_Done),
        onClick = onDoneClick,
    )
}

@Composable
private fun TransferContent(
    transaction: OfflineSignedTransaction,
    selectedFormat: OfflineTransactionFormat,
    qrCodeSaver: OfflineQrCodeSaver,
    modifier: Modifier = Modifier,
) {
    val qrContent = selectedFormat.content(transaction)
    val qrCodePainter = rememberPcashQrCodePainter(qrContent)

    TransferScrollableContent(
        modifier = modifier.fillMaxSize(),
        transaction = transaction,
        selectedFormat = selectedFormat,
        qrContent = qrContent,
        qrCodePainter = qrCodePainter,
        qrCodeSaver = qrCodeSaver
    )
}

@Composable
private fun TransferScrollableContent(
    transaction: OfflineSignedTransaction,
    selectedFormat: OfflineTransactionFormat,
    qrContent: String,
    qrCodePainter: Painter,
    qrCodeSaver: OfflineQrCodeSaver,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
    ) {
        VSpacer(12.dp)
        TransferHeader(selectedFormat)
        VSpacer(16.dp)
        QrCodePanel(
            qrContent = qrContent,
            qrCodePainter = qrCodePainter,
        )
        VSpacer(20.dp)
        TransferActionButtons(
            qrContent = qrContent,
            qrCodePainter = qrCodePainter,
            qrCodeSaver = qrCodeSaver,
        )
        VSpacer(24.dp)
        RawTransactionSection(rawHex = transaction.rawHex)
        VSpacer(24.dp)
    }
}

@Composable
private fun TransferHeader(selectedFormat: OfflineTransactionFormat) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        headline2_leah(
            text = stringResource(selectedFormat.transferTitleRes),
        )
        subhead2_grey(
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(R.string.offline_transaction_transfer_to_person_description),
        )
    }
}

@Composable
private fun QrCodePanel(
    qrContent: String,
    qrCodePainter: Painter,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = PcashQrCodeDefaults.Size)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ComposeAppTheme.colors.white),
            contentAlignment = Alignment.Center,
        ) {
            QrCodeImage(
                content = qrContent,
                qrcodePainter = qrCodePainter,
            )
        }
        VSpacer(12.dp)
        subhead2_grey(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            text = stringResource(R.string.offline_transaction_scan_qr_hint),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QrCodeImage(
    content: String,
    qrcodePainter: Painter,
) {
    PcashQrCodeImage(
        content = content,
        qrCodePainter = qrcodePainter,
    )
}

@Composable
private fun TransferActionButtons(
    qrContent: String,
    qrCodePainter: Painter,
    qrCodeSaver: OfflineQrCodeSaver,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val shareTitle = stringResource(R.string.Button_Share)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SaveQrActionButton(
            qrContent = qrContent,
            qrCodePainter = qrCodePainter,
            qrCodeSaver = qrCodeSaver,
            modifier = Modifier.weight(1f),
        )
        TransferActionButton(
            icon = R.drawable.ic_copy_20,
            text = stringResource(R.string.offline_transaction_copy_transaction),
            onClick = {
                TextHelper.copyText(qrContent)
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Copied)
            },
            modifier = Modifier.weight(1f),
        )
        TransferActionButton(
            icon = R.drawable.ic_share_24px,
            text = shareTitle,
            onClick = {
                context.startActivity(
                    Intent.createChooser(
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, qrContent)
                            type = "text/plain"
                        },
                        shareTitle,
                    )
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SaveQrActionButton(
    qrContent: String,
    qrCodePainter: Painter,
    qrCodeSaver: OfflineQrCodeSaver,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    TransferActionButton(
        icon = R.drawable.ic_download_20,
        text = stringResource(R.string.offline_transaction_save_qr),
        onClick = {
            if (isSaving) return@TransferActionButton
            isSaving = true
            scope.launch {
                if (saveQrCode(context, qrContent, qrCodePainter, density, qrCodeSaver)) {
                    HudHelper.showSuccessMessage(view, R.string.offline_transaction_qr_saved)
                } else {
                    HudHelper.showErrorMessage(view, R.string.offline_transaction_qr_save_failed)
                }
                isSaving = false
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun TransferActionButton(
    icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ButtonPrimaryCircle(
            icon = icon,
            onClick = onClick,
        )
        caption_grey(
            modifier = Modifier.padding(top = 8.dp),
            text = text,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private suspend fun saveQrCode(
    context: Context,
    content: String,
    painter: Painter,
    density: Density,
    qrCodeSaver: OfflineQrCodeSaver,
): Boolean =
    try {
        val bitmap = createPcashQrCodeBitmap(
            content = content,
            painter = painter,
            density = density,
        )
        try {
            qrCodeSaver.save(context, bitmap)
        } finally {
            bitmap.recycle()
        }
    } catch (_: Throwable) {
        false
    }

private fun OfflineTransactionFormat.content(transaction: OfflineSignedTransaction): String =
    when (this) {
        OfflineTransactionFormat.Pcash -> transaction.pcashPayload
        OfflineTransactionFormat.Raw -> transaction.rawHex
    }

private val OfflineTransactionFormat.transferTitleRes: Int
    get() = when (this) {
        OfflineTransactionFormat.Pcash -> R.string.offline_transaction_pcash_title
        OfflineTransactionFormat.Raw -> R.string.offline_transaction_raw_title
    }

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineTransactionTransferScreenPreview() {
    ComposeAppTheme {
        OfflineTransactionTransferScreen(
            transaction = previewOfflineSignedTransaction,
            selectedFormat = OfflineTransactionFormat.Raw,
            qrCodeSaver = OfflineQrCodeSaver(DefaultDispatcherProvider()),
            onBackClick = {},
            onDoneClick = {},
        )
    }
}

private val previewOfflineSignedTransaction = OfflineSignedTransaction(
    rawHex = "02000000000101d6a5b0c8b3b0cc8f0a08b6f4a6b9c8f1e2d3c4b5a69788776655443322110000" +
            "000000ffffffff02d00700000000000016001489abcdefabbaabbaabbaabbaabbaabbaabbaabba" +
            "8b00000000000000160014abcdef89abbaabbaabbaabbaabbaabbaabbaabba0247304402201f" +
            "2d3c4b5a697887766554433221100ffeeddccbbaa9988776655443322110002206f5e4d3c2b" +
            "1a0099887766554433221100ffeeddccbbaa99887766554433221100012102abcdefabcdefab" +
            "cdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdef00000000",
    pcashPayload = "pcash:tx:v1:bitcoin:eNqLrlZKSSxJVLJSykzOSC1KVrJS8kvMTVWyMjQwMFIyNjE1NLO0BAA0QQmG",
    txHash = "7c2a4ef0a8823a1d90f5d557b1c75675e5efb3d8802aa1d4e58c1cc3d3a3f8f2",
    createdAt = 0L,
)
