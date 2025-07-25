package cash.p.terminal.modules.zcashconfigure

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.activity.addCallback
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import io.horizontalsystems.core.setNavigationResultX
import cash.p.terminal.modules.enablecoin.restoresettings.ZCashConfig
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui.compose.components.TextPreprocessor
import cash.p.terminal.ui.compose.components.TextPreprocessorImpl
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellMultilineLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.body_grey50
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.findNavController
import cash.p.terminal.ui_compose.theme.ColoredTextStyle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.imageUrl
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class ZcashConfigure : BaseComposeFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        activity?.onBackPressedDispatcher?.addCallback(this) {
            close(findNavController())
        }
    }

    @Composable
    override fun GetContent(navController: NavController) {
        ZcashConfigureScreen(
            onCloseWithResult = { closeWithConfigt(it, navController) },
            onCloseClick = { close(navController) }
        )
    }

    private fun closeWithConfigt(config: ZCashConfig, navController: NavController) {
        navController.setNavigationResultX(Result(config))
        navController.popBackStack()
    }

    private fun close(navController: NavController) {
        navController.setNavigationResultX(Result(null))
        navController.popBackStack()
    }

    @Parcelize
    data class Result(val config: ZCashConfig?) : Parcelable
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZcashConfigureScreen(
    onCloseClick: () -> Unit,
    onCloseWithResult: (ZCashConfig) -> Unit,
    viewModel: ZcashConfigureViewModel = viewModel(),
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets
) {
    var showSlowSyncWarning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    viewModel.uiState.closeWithResult?.let {
        viewModel.onClosed()
        keyboardController?.hide()
        onCloseWithResult.invoke(it)
    }

    var textState by rememberSaveable("", stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    if (showSlowSyncWarning) {
        ModalBottomSheet(
            sheetState = sheetState,
            dragHandle = null,
            containerColor = ComposeAppTheme.colors.transparent,
            onDismissRequest = {
                showSlowSyncWarning = false
            }
        ) {
            SlowSyncWarningBottomSheet(
                text = stringResource(R.string.Restore_ZCash_SlowSyncWarningText),
                onContinueClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showSlowSyncWarning = false
                        }
                    }
                    viewModel.restoreAsOld()
                },
                onCloseClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showSlowSyncWarning = false
                        }
                    }
                },
            )
        }
    }
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = { ZcashAppBar(onCloseClick = onCloseClick) }
    ) {
        Column(modifier = Modifier.padding(it)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .windowInsetsPadding(windowInsets)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(12.dp))
                CellMultilineLawrenceSection(
                    listOf(
                        {
                            OptionCell(
                                title = stringResource(R.string.Restore_ZCash_NewWallet),
                                subtitle = stringResource(R.string.Restore_ZCash_NewWallet_Description),
                                checked = viewModel.uiState.restoreAsNew,
                                onClick = {
                                    viewModel.restoreAsNew()
                                    textState =
                                        textState.copy(text = "", selection = TextRange(0))
                                    focusManager.clearFocus()
                                }
                            )
                        },
                        {
                            OptionCell(
                                title = stringResource(R.string.Restore_ZCash_OldWallet),
                                subtitle = stringResource(R.string.Restore_ZCash_OldWallet_Description),
                                checked = viewModel.uiState.restoreAsOld,
                                onClick = {
                                    showSlowSyncWarning = true
                                    textState =
                                        textState.copy(text = "", selection = TextRange(0))
                                    focusManager.clearFocus()
                                }
                            )
                        },
                    )
                )

                Spacer(Modifier.height(24.dp))
                HeaderText(text = stringResource(R.string.Restore_BirthdayHeight))

                BirthdayHeightInput(
                    textState = textState,
                    focusRequester = focusRequester,
                    textPreprocessor = object : TextPreprocessor {
                        override fun process(text: String): String {
                            return text.replace("[^0-9]".toRegex(), "")
                        }
                    },
                    onValueChange = { textFieldValue ->
                        textState = textFieldValue
                        viewModel.setBirthdayHeight(textFieldValue.text)
                    }
                )

                InfoText(
                    text = stringResource(R.string.Restore_ZCash_BirthdayHeight_Hint),
                )

                Spacer(Modifier.height(24.dp))
            }

            ButtonsGroupWithShade {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    title = stringResource(R.string.Button_Done),
                    onClick = { viewModel.onDoneClick() },
                    enabled = viewModel.uiState.doneButtonEnabled
                )
            }
        }
    }
}

@Composable
private fun OptionCell(
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            body_leah(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            subhead2_grey(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.ic_checkmark_20),
                    tint = ComposeAppTheme.colors.jacob,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
fun ZcashAppBar(
    onCloseClick: () -> Unit,
) {
    AppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp),
                    painter = rememberAsyncImagePainterWithFallback(
                        model = BlockchainType.Zcash.imageUrl,
                        error = painterResource(R.drawable.ic_platform_placeholder_32)
                    ),
                    contentDescription = null
                )
                title3_leah(
                    text = stringResource(R.string.Restore_ZCash),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        menuItems = listOf(
            MenuItem(
                title = TranslatableString.ResString(R.string.Button_Close),
                icon = R.drawable.ic_close,
                onClick = onCloseClick
            )
        )
    )
}

@Composable
private fun SlowSyncWarningBottomSheet(
    text: String,
    onContinueClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.ic_attention_24),
        title = stringResource(R.string.Alert_TitleWarning),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
        onCloseClick = onCloseClick
    ) {
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = text
        )

        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp),
            title = stringResource(id = R.string.Button_Continue),
            onClick = onContinueClick
        )

        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            title = stringResource(id = R.string.Button_Cancel),
            onClick = onCloseClick
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun BirthdayHeightInput(
    textState: TextFieldValue,
    textPreprocessor: TextPreprocessor = TextPreprocessorImpl,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ComposeAppTheme.colors.steel20, RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .height(44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        BasicTextField(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .weight(1f),
            value = textState,
            onValueChange = { textFieldValue ->
                val textFieldValueProcessed =
                    textFieldValue.copy(text = textPreprocessor.process(textFieldValue.text))
                onValueChange.invoke(textFieldValueProcessed)
            },
            textStyle = ColoredTextStyle(
                color = ComposeAppTheme.colors.leah,
                textStyle = ComposeAppTheme.typography.body
            ),
            maxLines = 1,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            decorationBox = { innerTextField ->
                if (textState.text.isEmpty()) {
                    body_grey50(
                        modifier = Modifier.focusRequester(focusRequester),
                        text = "000000000",
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
                innerTextField()
            },
        )
    }
}

@Preview
@Composable
private fun Preview_ZcashConfigure() {
    ComposeAppTheme(darkTheme = false) {
        ZcashConfigureScreen({}, {})
    }
}
