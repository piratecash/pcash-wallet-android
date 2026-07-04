package cash.p.terminal.modules.paycore.verification

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.modules.paycore.PAYCORE_COMPLETE_BACK_URL
import cash.p.terminal.modules.paycore.PayCoreTicker
import cash.p.terminal.modules.paycore.webview.PayCoreWebViewScreen
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.body_lucian
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PayCoreVerificationScreen(
    networkType: PayCoreTicker,
    walletAddress: String,
    onClose: () -> Unit,
    onComplete: () -> Unit
) {
    val viewModel: PayCoreVerificationViewModel = koinViewModel(
        parameters = { parametersOf(networkType, walletAddress) }
    )
    val uiState = viewModel.uiState
    val currentOnComplete by rememberUpdatedState(onComplete)
    var activeKycUrl by rememberSaveable { mutableStateOf<String?>(null) }

    activeKycUrl?.let { kycUrl ->
        BackHandler {
            activeKycUrl = null
        }
        PayCoreWebViewScreen(
            url = kycUrl,
            title = stringResource(R.string.paycore_kyc_title),
            onClose = { activeKycUrl = null },
            onInterceptBackUrl = {
                activeKycUrl = null
                viewModel.onKycCompleted()
            },
            backUrlPrefix = PAYCORE_COMPLETE_BACK_URL
        )
        return
    }

    LaunchedEffect(uiState.completed) {
        if (uiState.completed) currentOnComplete()
    }

    PayCoreVerificationScreenContent(
        uiState = uiState,
        onClose = onClose,
        onPhoneChange = viewModel::onPhoneChange,
        onContinueClick = viewModel::onContinueClick,
        onAcceptVerificationWarning = viewModel::onVerificationWarningAccepted,
        onKycClick = { url ->
            activeKycUrl = url
        },
        onRetry = viewModel::onRetry
    )
}

@Composable
internal fun PayCoreVerificationScreenContent(
    uiState: PayCoreVerificationUiState,
    onClose: () -> Unit,
    onPhoneChange: (String) -> Unit,
    onContinueClick: () -> Unit,
    onAcceptVerificationWarning: () -> Unit,
    onKycClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.paycore_verification_title),
                navigationIcon = { HsBackButton(onClick = onClose) }
            )
        }
    ) { paddingValues ->
        when (uiState.screen) {
            VerificationScreen.PhoneInput ->
                PhoneInputScreen(
                    paddingValues = paddingValues,
                    phoneDigits = uiState.phone,
                    loading = uiState.loading,
                    error = uiState.error,
                    supportRequired = uiState.supportRequired,
                    onPhoneChange = onPhoneChange,
                    onContinueClick = onContinueClick
                )

            VerificationScreen.Processing ->
                ProcessingScreen(
                    paddingValues = paddingValues,
                    onRetry = onRetry
                )

            VerificationScreen.KycRequired ->
                KycRequiredScreen(
                    paddingValues = paddingValues,
                    kycUrl = uiState.kycUrl,
                    onKycClick = onKycClick
                )

            VerificationScreen.VerificationWarning ->
                VerificationWarningScreen(
                    paddingValues = paddingValues,
                    onAccept = onAcceptVerificationWarning,
                    onBack = onClose
                )

            VerificationScreen.Error ->
                ErrorScreen(
                    paddingValues = paddingValues,
                    message = uiState.error,
                    supportRequired = uiState.supportRequired,
                    onRetry = onRetry
                )
        }
    }
}

@Composable
private fun PhoneInputScreen(
    paddingValues: PaddingValues,
    phoneDigits: String,
    loading: Boolean,
    error: String?,
    supportRequired: Boolean,
    onPhoneChange: (String) -> Unit,
    onContinueClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        PhoneInputForm(
            phoneDigits = phoneDigits,
            loading = loading,
            error = error,
            supportRequired = supportRequired,
            onPhoneChange = onPhoneChange,
            onContinueClick = onContinueClick,
        )

        Spacer(modifier = Modifier.weight(1f))

        SupportLine()
    }
}

@Composable
private fun PhoneInputForm(
    phoneDigits: String,
    loading: Boolean,
    error: String?,
    supportRequired: Boolean,
    onPhoneChange: (String) -> Unit,
    onContinueClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        subhead2_grey(
            text = stringResource(R.string.paycore_verification_phone_label),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
        PhoneInputField(
            digits = phoneDigits,
            enabled = !loading,
            isError = error != null,
            onChangeDigits = onPhoneChange
        )
        PhoneInputHint(error = error, supportRequired = supportRequired)
        VSpacer(24.dp)
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = if (loading) {
                stringResource(R.string.paycore_verifying)
            } else {
                stringResource(R.string.paycore_verification_continue)
            },
            enabled = phoneDigits.length == 10 && !loading,
            loadingIndicator = loading,
            onClick = onContinueClick
        )
    }
}

@Composable
private fun PhoneInputHint(error: String?, supportRequired: Boolean) {
    when {
        supportRequired -> {
            VSpacer(8.dp)
            subhead2_grey(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                text = stringResource(R.string.paycore_verification_contact_support)
            )
        }

        error != null -> {
            VSpacer(8.dp)
            body_lucian(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                text = error
            )
        }
    }
}

@Composable
private fun SupportLine() {
    Text(
        text = AnnotatedString.fromHtml(
            htmlString = stringResource(R.string.paycore_verification_support_line),
            linkStyles = TextLinkStyles(
                style = SpanStyle(color = ComposeAppTheme.colors.jacob)
            )
        ),
        style = ComposeAppTheme.typography.subhead2,
        color = ComposeAppTheme.colors.grey,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

@Composable
private fun ProcessingScreen(
    paddingValues: PaddingValues,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LargeCircleWithSpinner()
            VSpacer(24.dp)
            subhead2_grey(text = stringResource(R.string.paycore_verification_processing))
            VSpacer(24.dp)
            ButtonPrimaryDefault(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.paycore_verification_retry),
                onClick = onRetry
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PhoneInputField(
    digits: String,
    enabled: Boolean,
    isError: Boolean,
    onChangeDigits: (String) -> Unit
) {
    val borderColor = if (isError) {
        ComposeAppTheme.colors.lucian
    } else {
        ComposeAppTheme.colors.steel20
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                borderColor,
                RoundedCornerShape(12.dp)
            )
            .background(ComposeAppTheme.colors.lawrence)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        body_leah(text = "+7 ")
        BasicTextField(
            value = digits,
            onValueChange = { onChangeDigits(normalizeRussianPhoneDigits(it)) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            textStyle = ComposeAppTheme.typography.body.copy(
                color = ComposeAppTheme.colors.leah
            ),
            singleLine = true,
            cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = RussianPhoneVisualTransformation,
            decorationBox = { inner ->
                Box {
                    if (digits.isEmpty()) {
                        body_grey(text = stringResource(R.string.paycore_verification_phone_placeholder))
                    }
                    inner()
                }
            }
        )
    }
}

private const val RUSSIAN_PHONE_DIGITS = 10

internal fun normalizeRussianPhoneDigits(value: String): String {
    val digits = value.filter(Char::isDigit)
    val normalizedDigits = when {
        digits.startsWith("8") -> digits.drop(1)
        digits.length > RUSSIAN_PHONE_DIGITS && digits.startsWith("7") -> digits.drop(1)
        else -> digits
    }

    return normalizedDigits.take(RUSSIAN_PHONE_DIGITS)
}

internal fun formatRussianPhoneMask(digits: String): String {
    return buildRussianPhoneMask(digits).text
}

internal object RussianPhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formattedPhone = buildRussianPhoneMask(text.text)

        return TransformedText(
            text = AnnotatedString(formattedPhone.text),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    return formattedPhone.originalToTransformed[
                        offset.coerceIn(0, formattedPhone.originalToTransformed.lastIndex)
                    ]
                }

                override fun transformedToOriginal(offset: Int): Int {
                    return formattedPhone.transformedToOriginal[
                        offset.coerceIn(0, formattedPhone.transformedToOriginal.lastIndex)
                    ]
                }
            }
        )
    }
}

private fun buildRussianPhoneMask(value: String): RussianPhoneMask {
    val digits = value.filter(Char::isDigit).take(RUSSIAN_PHONE_DIGITS)
    val transformedToOriginal = mutableListOf(0)
    val transformedText = StringBuilder(digits.length + 4)

    fun appendMask(char: Char, originalOffset: Int) {
        transformedText.append(char)
        transformedToOriginal.add(originalOffset)
    }

    fun appendDigit(char: Char, originalOffset: Int) {
        transformedText.append(char)
        transformedToOriginal.add(originalOffset)
    }

    if (digits.isNotEmpty()) {
        appendMask('(', 0)
    }

    digits.forEachIndexed { index, digit ->
        val originalOffset = index + 1
        appendDigit(digit, originalOffset)

        when (index) {
            2 -> appendMask(')', originalOffset)
            5, 7 -> appendMask('-', originalOffset)
        }
    }

    val originalToTransformed = IntArray(digits.length + 1)
    transformedToOriginal.forEachIndexed { transformedOffset, originalOffset ->
        originalToTransformed[originalOffset] = transformedOffset
    }

    return RussianPhoneMask(
        text = transformedText.toString(),
        originalToTransformed = originalToTransformed,
        transformedToOriginal = transformedToOriginal.toIntArray()
    )
}

private data class RussianPhoneMask(
    val text: String,
    val originalToTransformed: IntArray,
    val transformedToOriginal: IntArray
)

@Composable
private fun LargeCircleWithSpinner() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(ComposeAppTheme.colors.steel20),
        contentAlignment = Alignment.Center
    ) {
        HSCircularProgressIndicator()
    }
}

@Composable
private fun LargeCircleWithExclamation() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(ComposeAppTheme.colors.steel20),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sync_error),
            contentDescription = null,
            tint = ComposeAppTheme.colors.grey,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun KycRequiredScreen(
    paddingValues: PaddingValues,
    kycUrl: String?,
    onKycClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LargeCircleWithExclamation()
            VSpacer(24.dp)
            subhead2_grey(text = stringResource(R.string.paycore_verification_not_verified))
            VSpacer(24.dp)
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.paycore_verification_pass_kyc),
                enabled = kycUrl != null,
                onClick = { kycUrl?.let { onKycClick(it) } }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VerificationWarningScreen(
    paddingValues: PaddingValues,
    onAccept: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.weight(1f))
            headline1_leah(text = stringResource(R.string.paycore_verification_verified_title))
            VSpacer(16.dp)
            TextImportantWarning(
                title = stringResource(R.string.paycore_verification_warning_title),
                text = stringResource(R.string.paycore_verification_warning_text),
                icon = io.horizontalsystems.icons.R.drawable.ic_attention_24
            )
            VSpacer(32.dp)
            body_leah(
                text = stringResource(R.string.paycore_verification_warning_detail),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.weight(2f))
        }
        ButtonsGroupWithShade {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                ButtonPrimaryYellow(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.paycore_verification_continue),
                    onClick = onAccept
                )
                VSpacer(12.dp)
                ButtonPrimaryTransparent(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.paycore_verification_back),
                    onClick = onBack
                )
            }
        }
    }
}

@Composable
private fun ErrorScreen(
    paddingValues: PaddingValues,
    message: String?,
    supportRequired: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(24.dp)
            TextImportantError(
                text = if (supportRequired) {
                    stringResource(R.string.paycore_verification_contact_support)
                } else {
                    message ?: stringResource(R.string.paycore_verification_error)
                }
            )
        }
        ButtonsGroupWithShade {
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.paycore_verification_retry),
                onClick = onRetry
            )
        }
    }
}

// Previews

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PhoneInputScreenPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(screen = VerificationScreen.PhoneInput),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PhoneInputScreenErrorPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(
                screen = VerificationScreen.PhoneInput,
                phone = "9045556478",
                error = stringResource(R.string.paycore_verification_error)
            ),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun ProcessingScreenPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(
                screen = VerificationScreen.Processing,
                loading = true
            ),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun KycRequiredScreenPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(
                screen = VerificationScreen.KycRequired,
                kycUrl = "https://example.com/kyc"
            ),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PhoneInputSupportRequiredPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(
                screen = VerificationScreen.PhoneInput,
                phone = "9045556478",
                supportRequired = true
            ),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun VerificationWarningScreenPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(screen = VerificationScreen.VerificationWarning),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun VerificationWarningScreenDirectPreview() {
    ComposeAppTheme {
        VerificationWarningScreen(
            paddingValues = PaddingValues(),
            onAccept = {},
            onBack = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun ErrorScreenPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(screen = VerificationScreen.Error),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}
