package cash.p.terminal.ui.compose.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import io.horizontalsystems.core.slideFromRightForResult
import cash.p.terminal.core.utils.ModuleField
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.AddressParserViewModel
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.modules.contacts.ChooseContactFragment
import cash.p.terminal.modules.qrscanner.QRScannerActivity
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.entities.FormsInputStateWarning
import cash.p.terminal.ui_compose.theme.ColoredTextStyle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

@Composable
fun FormsInputAddress(
    modifier: Modifier = Modifier,
    value: String,
    hint: String,
    state: DataState<Address>? = null,
    showStateIcon: Boolean = true,
    textPreprocessor: TextPreprocessor = TextPreprocessorImpl,
    navController: NavController,
    chooseContactEnable: Boolean,
    blockchainType: BlockchainType?,
    onValueChange: (String) -> Unit,
    onAmountChange: (BigDecimal) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    val borderColor = when (state) {
        is DataState.Error -> {
            if (state.error is FormsInputStateWarning) {
                ComposeAppTheme.colors.yellow50
            } else {
                ComposeAppTheme.colors.red50
            }
        }
        else -> ComposeAppTheme.colors.steel20
    }

    val cautionColor = if (state?.errorOrNull is FormsInputStateWarning) {
        ComposeAppTheme.colors.jacob
    } else {
        ComposeAppTheme.colors.lucian
    }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .background(ComposeAppTheme.colors.lawrence),
            verticalAlignment = Alignment.CenterVertically
        ) {

            BasicTextField(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .weight(1f),
                enabled = true,
                value = value,
                onValueChange = { textFieldValue ->
                    val text = textPreprocessor.process(textFieldValue)
                    onValueChange.invoke(text)
                },
                textStyle = ColoredTextStyle(
                    color = ComposeAppTheme.colors.leah,
                    textStyle = ComposeAppTheme.typography.body
                ),
                singleLine = false,
                cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            hint,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = ComposeAppTheme.colors.grey50,
                            style = ComposeAppTheme.typography.body
                        )
                    }
                    innerTextField()
                },
                visualTransformation = VisualTransformation.None,
                keyboardOptions = KeyboardOptions.Default,
            )

            when (state) {
                is DataState.Loading -> {
                    HSCircularProgressIndicator()
                }
                is DataState.Error -> {
                    if(showStateIcon) {
                        Icon(
                            modifier = Modifier.padding(end = 8.dp),
                            painter = painterResource(id = R.drawable.ic_attention_20),
                            contentDescription = null,
                            tint = cautionColor
                        )
                    } else {
                        HSpacer(28.dp)
                    }
                }
                is DataState.Success -> {
                    if(showStateIcon) {
                        Icon(
                            modifier = Modifier.padding(end = 8.dp),
                            painter = painterResource(id = R.drawable.ic_check_20),
                            contentDescription = null,
                            tint = ComposeAppTheme.colors.remus
                        )
                    } else {
                        HSpacer(28.dp)
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.width(28.dp))
                }
            }

            if (value.isNotEmpty()) {
                ButtonSecondaryCircle(
                    modifier = Modifier.padding(end = 16.dp),
                    icon = R.drawable.ic_delete_20,
                    onClick = {
                        val text = textPreprocessor.process("")
                        onValueChange.invoke(text)
                        focusRequester.requestFocus()
                    }
                )
            } else {
                if (chooseContactEnable && blockchainType != null) {
                    ButtonSecondaryCircle(
                        modifier = Modifier.padding(end = 8.dp),
                        icon = R.drawable.ic_user_20,
                        onClick = {
                            navController.slideFromRightForResult<ChooseContactFragment.Result>(
                                R.id.chooseContact,
                                blockchainType
                            ) {
                                val textProcessed = textPreprocessor.process(it.address)
                                onValueChange.invoke(textProcessed)
                            }
                        }
                    )
                }
                val qrScannerLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            val scannedText =
                                result.data?.getStringExtra(ModuleField.SCAN_ADDRESS) ?: ""

                            val textProcessed = textPreprocessor.process(scannedText)
                            (textPreprocessor as? AddressParserViewModel)?.amountUnique?.amount?.let {
                                onAmountChange(it)
                            }
                            onValueChange.invoke(textProcessed)
                        }
                    }

                ButtonSecondaryCircle(
                    modifier = Modifier.padding(end = 8.dp),
                    icon = R.drawable.ic_qr_scan_20,
                    onClick = {
                        qrScannerLauncher.launch(QRScannerActivity.getScanQrIntent(context))
                    }
                )

                val clipboardManager = LocalClipboardManager.current
                ButtonSecondaryDefault(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .height(28.dp),
                    title = stringResource(id = R.string.Send_Button_Paste),
                    onClick = {
                        clipboardManager.getText()?.text?.let { textInClipboard ->
                            val textProcessed = textPreprocessor.process(textInClipboard)
                            (textPreprocessor as? AddressParserViewModel)?.amountUnique?.amount?.let {
                                    onAmountChange(it)
                            }
                            onValueChange.invoke(textProcessed)
                        }
                    },
                )
            }
        }

        state?.errorOrNull?.localizedMessage?.let {
            Text(
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                text = it,
                color = cautionColor,
                style = ComposeAppTheme.typography.caption
            )
        }
    }
}