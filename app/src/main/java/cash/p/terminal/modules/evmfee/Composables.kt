package cash.p.terminal.modules.evmfee

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.HSCaution
import cash.p.terminal.wallet.Warning
import cash.p.terminal.core.ethereum.CautionViewItem
import io.horizontalsystems.core.slideFromBottom
import cash.p.terminal.modules.evmfee.eip1559.Eip1559FeeSettingsViewModel
import cash.p.terminal.modules.evmfee.legacy.LegacyFeeSettingsViewModel
import cash.p.terminal.modules.fee.FeeCell
import cash.p.terminal.ui.compose.animations.shake
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ColoredTextStyle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import java.math.BigDecimal

@Composable
fun Eip1559FeeSettings(
    viewModel: Eip1559FeeSettingsViewModel,
    navController: NavController
) {
    val summaryViewItem = viewModel.feeSummaryViewItem
    val currentBaseFee = viewModel.currentBaseFee
    val maxFeeViewItem = viewModel.maxFeeViewItem
    val priorityFeeViewItem = viewModel.priorityFeeViewItem

    Column {
        Spacer(modifier = Modifier.height(12.dp))
        CellUniversalLawrenceSection(
            listOf(
                {
                    FeeCell(
                        title = stringResource(R.string.FeeSettings_NetworkFee),
                        info = stringResource(R.string.FeeSettings_NetworkFee_Info),
                        value = summaryViewItem?.fee,
                        viewState = summaryViewItem?.viewState,
                        navController = navController
                    )
                },
                {
                    FeeInfoCell(
                        title = stringResource(R.string.FeeSettings_GasLimit),
                        info = stringResource(R.string.FeeSettings_GasLimit_Info),
                        value = summaryViewItem?.gasLimit,
                        navController = navController
                    )
                },
                {
                    FeeInfoCell(
                        title = stringResource(R.string.FeeSettings_BaseFee),
                        info = stringResource(R.string.FeeSettings_BaseFee_Info),
                        value = currentBaseFee,
                        navController = navController
                    )
                }
            )
        )

        maxFeeViewItem?.let { maxFee ->
            priorityFeeViewItem?.let { priorityFee ->

                Spacer(modifier = Modifier.height(24.dp))
                EvmSettingsInput(
                    title = stringResource(R.string.FeeSettings_MaxFee),
                    info = stringResource(R.string.FeeSettings_MaxFee_Info),
                    value = BigDecimal(maxFee.weiValue).divide(BigDecimal(maxFee.scale.scaleValue)),
                    decimals = maxFee.scale.decimals,
                    warnings = maxFee.warnings,
                    errors = maxFee.errors,
                    navController = navController,
                    onValueChange = {
                        viewModel.onSelectGasPrice(maxFee.wei(it), priorityFee.weiValue)
                    },
                    onClickIncrement = {
                        viewModel.onIncrementMaxFee(maxFee.weiValue, priorityFee.weiValue)
                    },
                    onClickDecrement = {
                        viewModel.onDecrementMaxFee(maxFee.weiValue, priorityFee.weiValue)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
                EvmSettingsInput(
                    title = stringResource(R.string.FeeSettings_MaxMinerTips),
                    info = stringResource(R.string.FeeSettings_MaxMinerTips_Info),
                    value = BigDecimal(priorityFee.weiValue).divide(BigDecimal(priorityFee.scale.scaleValue)),
                    decimals = priorityFee.scale.decimals,
                    warnings = priorityFee.warnings,
                    errors = priorityFee.errors,
                    navController = navController,
                    onValueChange = {
                        viewModel.onSelectGasPrice(maxFee.weiValue, priorityFee.wei(it))
                    },
                    onClickIncrement = {
                        viewModel.onIncrementPriorityFee(maxFee.weiValue, priorityFee.weiValue)
                    },
                    onClickDecrement = {
                        viewModel.onDecrementPriorityFee(maxFee.weiValue, priorityFee.weiValue)
                    }
                )
            }
        }
    }
}

@Composable
fun EvmSettingsInput(
    title: String,
    info: String,
    value: BigDecimal,
    decimals: Int,
    warnings: List<Warning>,
    errors: List<Throwable>,
    navController: NavController,
    onValueChange: (BigDecimal) -> Unit,
    onClickIncrement: () -> Unit,
    onClickDecrement: () -> Unit
) {
    val borderColor = when {
        errors.isNotEmpty() -> cash.p.terminal.ui_compose.theme.ComposeAppTheme.colors.red50
        warnings.isNotEmpty() -> cash.p.terminal.ui_compose.theme.ComposeAppTheme.colors.yellow50
        else -> cash.p.terminal.ui_compose.theme.ComposeAppTheme.colors.steel20
    }

    EvmSettingsInput(
        title = title,
        info = info,
        value = value,
        decimals = decimals,
        borderColor = borderColor,
        navController = navController,
        onValueChange = onValueChange,
        onClickIncrement = onClickIncrement,
        onClickDecrement = onClickDecrement
    )
}

@Composable
fun EvmSettingsInput(
    title: String,
    info: String,
    value: BigDecimal,
    decimals: Int,
    caution: HSCaution?,
    navController: NavController,
    onValueChange: (BigDecimal) -> Unit,
    onClickIncrement: () -> Unit,
    onClickDecrement: () -> Unit
) {
    val borderColor = when (caution?.type) {
        HSCaution.Type.Error -> cash.p.terminal.ui_compose.theme.ComposeAppTheme.colors.red50
        HSCaution.Type.Warning -> cash.p.terminal.ui_compose.theme.ComposeAppTheme.colors.yellow50
        else -> cash.p.terminal.ui_compose.theme.ComposeAppTheme.colors.steel20
    }

    EvmSettingsInput(
        title = title,
        info = info,
        value = value,
        decimals = decimals,
        borderColor = borderColor,
        navController = navController,
        onValueChange = onValueChange,
        onClickIncrement = onClickIncrement,
        onClickDecrement = onClickDecrement
    )
}

@Composable
private fun EvmSettingsInput(
    title: String,
    info: String,
    value: BigDecimal,
    decimals: Int,
    borderColor: Color,
    navController: NavController,
    onValueChange: (BigDecimal) -> Unit,
    onClickIncrement: () -> Unit,
    onClickDecrement: () -> Unit,
) {
    HeaderText(text = title) {
        navController.slideFromBottom(R.id.feeSettingsInfoDialog, FeeSettingsInfoDialog.Input(title, info))
    }

    NumberInputWithButtons(value, decimals, borderColor, onValueChange, onClickIncrement, onClickDecrement)
}

@Composable
private fun NumberInputWithButtons(
    value: BigDecimal,
    decimals: Int,
    borderColor: Color,
    onValueChange: (BigDecimal) -> Unit,
    onClickIncrement: () -> Unit,
    onClickDecrement: () -> Unit
) {
    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = value.toString()
        mutableStateOf(TextFieldValue(text))
    }
    var playShakeAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        textState = textState.copy(text = value.toString(), selection = TextRange("$value".length))
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence),
        verticalAlignment = Alignment.CenterVertically
    ) {

        BasicTextField(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .weight(1f)
                .shake(
                    enabled = playShakeAnimation,
                    onAnimationFinish = { playShakeAnimation = false }
                ),
            value = textState,
            onValueChange = { textFieldValue ->
                val newValue = textFieldValue.text.toBigDecimalOrNull() ?: BigDecimal.ZERO
                if (newValue.scale() <= decimals) {
                    val currentText = textState.text
                    textState = textFieldValue
                    if (currentText != textFieldValue.text) {
                        onValueChange(newValue)
                    }
                } else {
                    playShakeAnimation = true
                }
            },
            textStyle = ColoredTextStyle(
                color = ComposeAppTheme.colors.leah,
                textStyle = ComposeAppTheme.typography.body
            ),
            singleLine = true,
            cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        ButtonSecondaryCircle(
            modifier = Modifier.padding(end = 16.dp),
            icon = R.drawable.ic_minus_20,
            onClick = onClickDecrement
        )

        ButtonSecondaryCircle(
            modifier = Modifier.padding(end = 16.dp),
            icon = R.drawable.ic_plus_20,
            onClick = onClickIncrement
        )
    }
}

@Composable
fun ButtonsGroupWithShade(
    modifier: Modifier = Modifier,
    ButtonsContent: @Composable (() -> Unit)
) {
    Column(
        modifier = modifier.offset(y = -(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(ComposeAppTheme.colors.transparent, ComposeAppTheme.colors.tyler)
                    )
                )
        )
        Box(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(bottom = 8.dp) // With 24dp offset actual padding will be 32dp
        ) {
            ButtonsContent()
        }
    }
}

@Composable
fun LegacyFeeSettings(
    viewModel: LegacyFeeSettingsViewModel,
    navController: NavController
) {
    val summaryViewItem = viewModel.feeSummaryViewItem
    val viewItem = viewModel.feeViewItem

    Column {
        Spacer(modifier = Modifier.height(12.dp))
        CellUniversalLawrenceSection(
            listOf(
                {
                    FeeCell(
                        title = stringResource(R.string.FeeSettings_NetworkFee),
                        info = stringResource(R.string.FeeSettings_NetworkFee_Info),
                        value = summaryViewItem?.fee,
                        viewState = summaryViewItem?.viewState,
                        navController = navController
                    )
                },
                {
                    FeeInfoCell(
                        title = stringResource(R.string.FeeSettings_GasLimit),
                        info = stringResource(R.string.FeeSettings_GasLimit_Info),
                        value = summaryViewItem?.gasLimit,
                        navController = navController
                    )
                }
            )
        )

        viewItem?.let { fee ->
            Spacer(modifier = Modifier.height(24.dp))
            EvmSettingsInput(
                title = stringResource(R.string.FeeSettings_GasPrice),
                info = stringResource(R.string.FeeSettings_GasPrice_Info),
                value = BigDecimal(fee.weiValue).divide(BigDecimal(fee.scale.scaleValue)),
                decimals = fee.scale.decimals,
                warnings = fee.warnings,
                errors = fee.errors,
                navController = navController,
                onValueChange = {
                    viewModel.onSelectGasPrice(fee.wei(it))
                },
                onClickIncrement = {
                    viewModel.onIncrementGasPrice(fee.weiValue)
                },
                onClickDecrement = {
                    viewModel.onDecrementGasPrice(fee.weiValue)
                }
            )
        }
    }
}

@Composable
fun Cautions(cautions: List<CautionViewItem>) {
    VSpacer(16.dp)

    val modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        cautions.forEach { caution ->

            when (caution.type) {
                CautionViewItem.Type.Error -> {
                    TextImportantError(
                        modifier = modifier,
                        text = caution.text,
                        title = caution.title,
                        icon = R.drawable.ic_attention_20
                    )
                }
                CautionViewItem.Type.Warning -> {
                    TextImportantWarning(
                        modifier = modifier,
                        text = caution.text,
                        title = caution.title,
                        icon = R.drawable.ic_attention_20
                    )
                }
            }
        }
    }
}

@Composable
fun FeeInfoCell(
    title: String,
    info: String,
    value: String?,
    navController: NavController
) {
    RowUniversal(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.clickable(
                onClick = { navController.slideFromBottom(R.id.feeSettingsInfoDialog, FeeSettingsInfoDialog.Input(title, info)) },
                interactionSource = MutableInteractionSource(),
                indication = null
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            subhead2_grey(text = title)

            Image(
                modifier = Modifier.padding(horizontal = 8.dp),
                painter = painterResource(id = R.drawable.ic_info_20),
                contentDescription = ""
            )
        }

        subhead1_leah(
            modifier = Modifier.weight(1f),
            text = value ?: "",
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

