package cash.p.terminal.modules.multiswap.exchange

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.modules.multiswap.EstimationTimeBadge
import cash.p.terminal.modules.multiswap.PriceField
import cash.p.terminal.modules.multiswap.PriceImpactField
import cash.p.terminal.modules.multiswap.ProviderRiskBadge
import cash.p.terminal.modules.multiswap.providers.ProviderRiskType
import cash.p.terminal.modules.multiswap.ui.SwapProviderField
import cash.p.terminal.ui.compose.components.HSRow
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsImageCircle
import cash.p.terminal.ui_compose.components.MenuItemTimeoutIndicator
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiSwapExchangeScreen(
    uiState: MultiSwapExchangeUiState?,
    timeRemainingProgress: () -> Float?,
    onSwap: () -> Unit,
    onRefresh: () -> Unit,
    onContinueLater: () -> Unit,
    onDeleteAndClose: () -> Unit,
    onBack: () -> Unit,
    onClickProvider: () -> Unit,
    onClickLeg1: () -> Unit = {},
    swapButtonTitle: String = stringResource(R.string.Swap),
) {
    var showCancelConfirmation by remember { mutableStateOf(false) }

    if (showCancelConfirmation) {
        CancelSwapBottomSheet(
            onConfirm = {
                showCancelConfirmation = false
                onDeleteAndClose()
            },
            onDismiss = { showCancelConfirmation = false },
        )
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(
                    if (uiState?.presentation == MultiSwapExchangePresentation.RouteInfo) {
                        R.string.multi_swap_route_title
                    } else {
                        R.string.Swap
                    }
                ),
                navigationIcon = { HsBackButton(onClick = onBack) },
                menuItems = buildList {
                    timeRemainingProgress()?.let { progress ->
                        add(MenuItemTimeoutIndicator(progress))
                    }
                },
            )
        }
    ) { paddingValues ->
        if (uiState == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                VSpacer(height = 12.dp)
                if (uiState.presentation == MultiSwapExchangePresentation.RouteInfo) {
                    RouteInfoHeader(uiState)
                }
                // Leg1 header center = 24dp, Leg2 header center = 20dp
                val dotStartPadding = 8.dp
                val cardStartPadding = dotStartPadding + 8.dp + 8.dp // dot area + gap
                val leg1DotOffset = 24.dp - 4.dp // header center - half dot
                var leg1CardHeight by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                val gapBetweenCards = 23.dp
                val leg2HeaderCenter = 20.dp
                val leg2DotOffset = with(density) {
                    leg1CardHeight.toDp() + gapBetweenCards + leg2HeaderCenter - 4.dp
                }

                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                ) {
                    // Cards column
                    Column(
                        modifier = Modifier.padding(start = cardStartPadding)
                    ) {
                        StepLabel(
                            index = 1,
                            visible = uiState.presentation == MultiSwapExchangePresentation.RouteInfo
                        )
                        LegCard(
                            leg = uiState.leg1,
                            borderColor = ComposeAppTheme.colors.grey,
                            modifier = Modifier.onSizeChanged { leg1CardHeight = it.height },
                            content = {

                                Leg1Header(
                                    providerName = uiState.leg1.providerName,
                                    status = uiState.leg1.status,
                                    coinIconUrlIn = uiState.leg1.coinIconUrlIn,
                                    coinIconUrlOut = uiState.leg1.coinIconUrlOut,
                                    riskType = uiState.leg1.riskType,
                                    estimationTime = uiState.leg1.estimationTime,
                                    onClick = if (uiState.leg1Clickable) onClickLeg1 else null,
                                )
                                LegContent(
                                    leg = uiState.leg1,
                                    providerName = uiState.leg1.providerName,
                                    providerIcon = uiState.leg1.providerIcon,
                                )
                            }
                        )
                        VSpacer(height = gapBetweenCards)
                        StepLabel(
                            index = 2,
                            visible = uiState.presentation == MultiSwapExchangePresentation.RouteInfo
                        )
                        LegCard(
                            leg = uiState.leg2,
                            borderColor = ComposeAppTheme.colors.steel20,
                            content = {
                                Leg2Header(
                                    providerName = uiState.leg2.providerName,
                                    providerIcon = uiState.leg2.providerIcon,
                                    clickable = uiState.leg2ProviderClickable,
                                    quoting = uiState.leg2Quoting,
                                    onClickProvider = onClickProvider,
                                    riskType = uiState.leg2.riskType,
                                    estimationTime = uiState.leg2.estimationTime,
                                )
                                LegContent(uiState.leg2)
                            }
                        )
                    }
                    if (uiState.presentation == MultiSwapExchangePresentation.Execution) {
                        StatusDot(
                            status = uiState.leg1.status,
                            modifier = Modifier
                                .padding(start = dotStartPadding)
                                .offset(y = leg1DotOffset),
                        )
                        StatusDot(
                            status = uiState.leg2.status,
                            modifier = Modifier
                                .padding(start = dotStartPadding)
                                .offset(y = leg2DotOffset),
                        )
                        VerticalLine(
                            isDotted = uiState.leg1.status != LegStatus.Completed,
                            modifier = Modifier
                                .padding(start = dotStartPadding + 4.5.dp)
                                .offset(y = leg1DotOffset + 15.dp)
                                .height(leg2DotOffset - leg1DotOffset - 20.dp),
                        )
                    }
                }
                VSpacer(height = 24.dp)
            }

            if (uiState.presentation == MultiSwapExchangePresentation.RouteInfo) {
                RouteInfoButtons(
                    buttonState = uiState.buttonState,
                    onContinue = onSwap,
                    onRefresh = onRefresh,
                    onCancel = onBack,
                )
            } else BottomButtons(
                buttonState = uiState.buttonState,
                showContinueLater = uiState.showContinueLater,
                swapButtonTitle = swapButtonTitle,
                onSwap = onSwap,
                onRefresh = onRefresh,
                onClose = onContinueLater,
                onDeleteAndClose = { showCancelConfirmation = true },
            )
        }
    }
}

@Composable
private fun RouteInfoHeader(uiState: MultiSwapExchangeUiState) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        uiState.routeExplanationTokens.takeIf { it.size == 3 }?.let { tokens ->
            val routeExplanation = stringResource(
                R.string.multi_swap_route_explanation,
                tokens[0],
                tokens[1],
                tokens[2],
            )
            TextImportantWarning(
                text = remember(routeExplanation) { routeExplanation.withCenteredRouteArrows() },
            )
        }
        VSpacer(height = 16.dp)
    }
}

private fun String.withCenteredRouteArrows(): AnnotatedString = buildAnnotatedString {
    append(this@withCenteredRouteArrows)
    this@withCenteredRouteArrows.forEachIndexed { index, char ->
        if (char == '→') {
            addStyle(
                style = SpanStyle(baselineShift = BaselineShift(0.12f)),
                start = index,
                end = index + 1,
            )
        }
    }
}

@Composable
private fun RouteInfoButtons(
    buttonState: ButtonState,
    onContinue: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    ButtonsGroupWithShade {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SwapPrimaryButton(
                buttonState = buttonState,
                modifier = buttonModifier,
                title = stringResource(R.string.multi_swap_understood_continue),
                onSwap = onContinue,
                onRefresh = onRefresh,
                onClose = onCancel,
            )
            VSpacer(height = 8.dp)
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.Button_Cancel),
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun StepLabel(index: Int, visible: Boolean) {
    if (visible) {
        subhead1_grey(
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            text = stringResource(R.string.multi_swap_step, index),
        )
    }
}

@Composable
private fun StatusDot(status: LegStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(statusDotColor(status))
    )
}

@Composable
private fun VerticalLine(isDotted: Boolean, modifier: Modifier = Modifier) {
    val color = ComposeAppTheme.colors.grey
    if (isDotted) {
        Canvas(modifier = modifier.width(1.dp)) {
            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            drawLine(
                color = color,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = pathEffect,
            )
        }
    } else {
        Box(
            modifier = modifier
                .width(1.dp)
                .background(color)
        )
    }
}

@Composable
private fun LegCard(
    leg: LegUiState,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence)
    ) {
        content()
    }
}

@Composable
private fun LegContent(
    leg: LegUiState,
    providerName: String? = null,
    providerIcon: Int? = null,
) {
    // You Send
    AmountRow(
        title = stringResource(R.string.swap_you_send),
        badge = leg.badgeIn,
        amountFormatted = leg.amountInFormatted?.let { "$it ${leg.coinIn}" },
        fiatAmount = leg.fiatAmountIn,
        currency = leg.currency,
        amountColor = ComposeAppTheme.colors.leah,
    )
    // You Get
    AmountRow(
        title = stringResource(R.string.swap_you_receive),
        badge = leg.badgeOut,
        amountFormatted = leg.amountOutFormatted?.let { "$it ${leg.coinOut}" },
        fiatAmount = leg.fiatAmountOut,
        currency = leg.currency,
        amountColor = ComposeAppTheme.colors.remus,
    )
    if (providerName != null && providerIcon != null) {
        SwapProviderField(providerName, providerIcon)
    }
    // Price
    val tokenIn = leg.tokenIn
    val tokenOut = leg.tokenOut
    val amountIn = leg.amountIn
    val amountOut = leg.amountOut
    if (tokenIn != null && tokenOut != null && amountIn != null && amountOut != null) {
        PriceField(tokenIn, tokenOut, amountIn, amountOut, borderTop = true)
        PriceImpactField(leg.priceImpact, leg.priceImpactLevel)
    }
}

@Composable
private fun AmountRow(
    title: String,
    badge: String?,
    amountFormatted: String?,
    fiatAmount: BigDecimal?,
    currency: Currency?,
    amountColor: Color,
) {
    CellUniversal(borderTop = true) {
        Column {
            subhead2_leah(text = title)
            VSpacer(height = 1.dp)
            caption_grey(text = badge ?: stringResource(R.string.CoinPlatforms_Native))
        }
        HFillSpacer(minWidth = 16.dp)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountFormatted ?: "---",
                style = ComposeAppTheme.typography.subhead1,
                color = amountColor,
            )
            if (fiatAmount != null && currency != null) {
                VSpacer(height = 1.dp)
                caption_grey(text = CurrencyValue(currency, fiatAmount).getFormattedFull())
            }
        }
    }
}

@Composable
private fun ProviderBadges(
    riskType: ProviderRiskType?,
    estimationTime: Long?,
    modifier: Modifier = Modifier,
) {
    if (riskType == null && estimationTime == null) return
    VSpacer(height = 4.dp)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        estimationTime?.let { EstimationTimeBadge(seconds = it) }
        riskType?.let { ProviderRiskBadge(riskType = it) }
    }
}

@Composable
private fun Leg1Header(
    providerName: String?,
    status: LegStatus,
    coinIconUrlIn: String?,
    coinIconUrlOut: String?,
    riskType: ProviderRiskType? = null,
    estimationTime: Long? = null,
    onClick: (() -> Unit)? = null,
) {
    CellUniversal(
        borderTop = false,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (status == LegStatus.Executing) {
                HSCircularProgressIndicator(progress = 0.15f)
            }
            HsImageCircle(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 4.dp, start = 6.dp)
                    .size(24.dp),
                url = coinIconUrlIn,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 4.5.dp, end = 6.5.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ComposeAppTheme.colors.tyler)
            )
            HsImageCircle(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 4.dp, end = 6.dp)
                    .size(24.dp),
                url = coinIconUrlOut,
            )
        }
        HSpacer(width = 16.dp)
        Column {
            val titleText = when {
                providerName == null -> stringResource(R.string.Swap)
                status == LegStatus.Completed ->
                    stringResource(R.string.multi_swap_completed_via, providerName)

                else -> stringResource(R.string.multi_swap_via, providerName)
            }
            subhead1_leah(text = titleText)
            ProviderBadges(riskType = riskType, estimationTime = estimationTime)
        }
    }
}

@Composable
private fun Leg2Header(
    providerName: String?,
    providerIcon: Int?,
    clickable: Boolean,
    quoting: Boolean,
    onClickProvider: () -> Unit,
    riskType: ProviderRiskType? = null,
    estimationTime: Long? = null,
) {
    HSRow(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        borderBottom = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = clickable,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClickProvider,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (providerIcon != null) {
                Image(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(providerIcon),
                    contentDescription = null
                )
                HSpacer(width = 8.dp)
            }
            when {
                providerName != null -> Column {
                    subhead1_leah(text = stringResource(R.string.multi_swap_via, providerName))
                    ProviderBadges(riskType = riskType, estimationTime = estimationTime)
                }

                quoting -> subhead1_grey(text = stringResource(R.string.multi_swap_finding_best_provider))
                else -> subhead1_grey(text = stringResource(R.string.multi_swap_no_providers))
            }
            if (clickable) {
                HFillSpacer(minWidth = 16.dp)
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey,
                )
            }
        }
    }
}

@Composable
private fun BottomButtons(
    buttonState: ButtonState,
    showContinueLater: Boolean,
    swapButtonTitle: String,
    onSwap: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    onDeleteAndClose: () -> Unit,
) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)

    ButtonsGroupWithShade {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SwapPrimaryButton(
                buttonState = buttonState,
                modifier = buttonModifier,
                title = swapButtonTitle,
                onSwap = onSwap,
                onRefresh = onRefresh,
                onClose = onClose,
            )

            if (showContinueLater) {
                VSpacer(height = 8.dp)
                ButtonPrimaryTransparent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    title = stringResource(R.string.swap_continue_later),
                    onClick = onClose,
                )
                VSpacer(height = 8.dp)
                ButtonPrimaryTransparent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    title = stringResource(R.string.Button_Cancel),
                    textColor = ComposeAppTheme.colors.lucian,
                    onClick = onDeleteAndClose,
                )
            }
        }
    }
}

@Composable
private fun SwapPrimaryButton(
    buttonState: ButtonState,
    title: String,
    onSwap: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (buttonState == ButtonState.Hidden) return
    if (buttonState == ButtonState.Refresh) {
        ButtonPrimaryDefault(
            modifier = modifier,
            title = stringResource(R.string.Button_Refresh),
            onClick = onRefresh,
        )
        return
    }

    val enabled = buttonState == ButtonState.Enabled || buttonState == ButtonState.Close
    val buttonTitle = when (buttonState) {
        ButtonState.Close -> stringResource(R.string.Button_Close)
        ButtonState.Quoting -> stringResource(R.string.Swap_Quoting)
        else -> title
    }
    val onClick: () -> Unit = when (buttonState) {
        ButtonState.Close -> onClose
        ButtonState.Enabled -> onSwap
        else -> ({})
    }
    ButtonPrimaryYellow(
        modifier = modifier,
        title = buttonTitle,
        enabled = enabled,
        loadingIndicator = buttonState == ButtonState.Quoting,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CancelSwapBottomSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val buttonModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)

        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.ic_attention_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.lucian),
            title = stringResource(R.string.multi_swap_cancel_swap),
            onCloseClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            },
        ) {
            VSpacer(12.dp)
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(R.string.multi_swap_cancel_swap_warning),
            )
            VSpacer(32.dp)
            ButtonPrimaryRed(
                modifier = buttonModifier,
                title = stringResource(R.string.Button_Delete),
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm() }
                },
            )
            VSpacer(12.dp)
            ButtonPrimaryTransparent(
                modifier = buttonModifier,
                title = stringResource(R.string.Button_Cancel),
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
            )
            VSpacer(32.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PayCoreDeleteRestrictedBottomSheet(
    requiresBankSelection: Boolean,
    onSelectBank: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.ic_attention_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            title = stringResource(R.string.Alert_TitleWarning),
            onCloseClick = { dismiss() },
        ) {
            VSpacer(12.dp)
            val warningText = if (requiresBankSelection) {
                stringResource(R.string.paycore_delete_restricted_warning) + " " +
                        stringResource(R.string.paycore_delete_to_complete_select_bank)
            } else {
                stringResource(R.string.paycore_delete_restricted_warning)
            }
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = warningText,
            )
            VSpacer(32.dp)
            if (requiresBankSelection) {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    title = stringResource(R.string.paycore_select_bank),
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onSelectBank() }
                    },
                )
            } else {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    title = stringResource(R.string.Button_Close),
                    onClick = { dismiss() },
                )
            }
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun statusDotColor(status: LegStatus) = when (status) {
    LegStatus.Pending -> ComposeAppTheme.colors.grey
    LegStatus.Executing -> ComposeAppTheme.colors.jacob
    LegStatus.Completed -> ComposeAppTheme.colors.remus
    LegStatus.Failed -> ComposeAppTheme.colors.lucian
}

@Preview
@Composable
private fun Leg1HeaderPreview() {
    ComposeAppTheme {
        Column {
            Leg1Header(
                providerName = "STON.fi",
                status = LegStatus.Executing,
                coinIconUrlIn = null,
                coinIconUrlOut = null,
                riskType = ProviderRiskType.Flexible,
            )
            Leg1Header(
                providerName = "STON.fi",
                status = LegStatus.Completed,
                coinIconUrlIn = null,
                coinIconUrlOut = null,
                riskType = ProviderRiskType.Auto,
            )
        }
    }
}

@Preview(name = "Route info")
@Composable
private fun MultiSwapRouteInfoPreview() {
    MultiSwapScreenPreviewContent(
        presentation = MultiSwapExchangePresentation.RouteInfo,
        leg1Status = LegStatus.Pending,
        buttonState = ButtonState.Enabled,
    )
}

@Preview(name = "Execution")
@Composable
private fun MultiSwapExchangeScreenPreview() {
    MultiSwapScreenPreviewContent(
        presentation = MultiSwapExchangePresentation.Execution,
        leg1Status = LegStatus.Executing,
        buttonState = ButtonState.Disabled,
        showContinueLater = true,
    )
}

@Preview(name = "Completed execution")
@Composable
private fun MultiSwapExchangeScreenCompletedPreview() {
    MultiSwapScreenPreviewContent(
        presentation = MultiSwapExchangePresentation.Execution,
        leg1Status = LegStatus.Completed,
        buttonState = ButtonState.Enabled,
        showContinueLater = true,
        timeRemainingProgress = 0.7f,
    )
}

@Composable
private fun MultiSwapScreenPreviewContent(
    presentation: MultiSwapExchangePresentation,
    leg1Status: LegStatus,
    buttonState: ButtonState,
    showContinueLater: Boolean = false,
    timeRemainingProgress: Float? = null,
) {
    ComposeAppTheme(darkTheme = true) {
        MultiSwapExchangeScreen(
            uiState = previewUiState(presentation, leg1Status, buttonState, showContinueLater),
            timeRemainingProgress = { timeRemainingProgress },
            onSwap = {},
            onRefresh = {},
            onContinueLater = {},
            onDeleteAndClose = {},
            onBack = {},
            onClickProvider = {},
        )
    }
}

private fun previewUiState(
    presentation: MultiSwapExchangePresentation,
    leg1Status: LegStatus,
    buttonState: ButtonState,
    showContinueLater: Boolean,
) = MultiSwapExchangeUiState(
    leg1 = previewLeg1(leg1Status),
    leg2 = previewLeg2(),
    buttonState = buttonState,
    showContinueLater = showContinueLater,
    leg2ProviderClickable = presentation == MultiSwapExchangePresentation.RouteInfo,
    presentation = presentation,
    routeExplanationTokens = listOf("PIRATE", "TONCOIN", "BNB"),
)

private fun previewLeg1(status: LegStatus) = LegUiState(
    status = status,
    providerName = "STON.fi",
    providerIcon = R.drawable.ic_ston_fi,
    coinIn = "PIRATE",
    coinOut = "TONCOIN",
    amountInFormatted = "100",
    amountOutFormatted = "12.5",
    badgeIn = "BTC",
    badgeOut = "TON",
    riskType = ProviderRiskType.Auto,
    estimationTime = 797L,
)

private fun previewLeg2() = LegUiState(
    status = LegStatus.Pending,
    providerName = "ChangeNow",
    providerIcon = R.drawable.ic_change_now,
    coinIn = "TONCOIN",
    coinOut = "BNB",
    amountInFormatted = "12.5",
    amountOutFormatted = "0.8",
    badgeIn = "TON",
    badgeOut = "BSC",
    riskType = ProviderRiskType.Controlled,
    estimationTime = 793L,
)
