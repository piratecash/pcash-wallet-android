package cash.p.terminal.modules.settings.appearance

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.AppCloseWarningBottomSheet
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.PremiumHeader
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_jacob
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppIconScreen(
    appIconOptions: Select<AppIcon>,
    onAppIconSelect: (AppIcon) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pendingAppIcon by remember { mutableStateOf<AppIcon?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    pendingAppIcon?.let { icon ->
        AppCloseWarningBottomSheet(
            sheetState = sheetState,
            onDismiss = { pendingAppIcon = null },
            onConfirm = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    pendingAppIcon = null
                    onAppIconSelect(icon)
                }
            },
        )
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.Appearance_AppIcon),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
        ) {
            VSpacer(12.dp)
            AppIconContent(appIconOptions) { appIcon ->
                pendingAppIcon = appIcon
            }
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun AppIconContent(
    appIconOptions: Select<AppIcon>,
    onAppIconSelect: (AppIcon) -> Unit
) {
    val ourIcons = remember(appIconOptions.options) {
        appIconOptions.options.filter { it.category == AppIconCategory.OUR }
    }
    val premiumIcons = remember(appIconOptions.options) {
        appIconOptions.options.filter { it.category == AppIconCategory.OUR_PREMIUM }
    }
    val otherIcons = remember(appIconOptions.options) {
        appIconOptions.options.filter { it.category == AppIconCategory.OTHER }
    }

    HeaderText(text = stringResource(R.string.appearance_app_icon_our_icon))
    AppIconCard(ourIcons, appIconOptions.selected, onAppIconSelect)

    if (premiumIcons.isNotEmpty()) {
        VSpacer(16.dp)
        PremiumHeader(
            text = stringResource(R.string.appearance_app_icon_premium),
            horizontalPadding = 16.dp,
        )
        AppIconCard(premiumIcons, appIconOptions.selected, onAppIconSelect, premium = true)
    }

    VSpacer(16.dp)

    HeaderText(text = stringResource(R.string.appearance_app_icon_other))
    AppIconCard(otherIcons, appIconOptions.selected, onAppIconSelect)
}

@Composable
private fun AppIconCard(
    icons: List<AppIcon>,
    selected: AppIcon,
    onAppIconSelect: (AppIcon) -> Unit,
    premium: Boolean = false,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icons.chunked(3).forEach { row ->
            AppIconsRow(row, selected, onAppIconSelect, premium)
        }
    }
}

@Composable
private fun AppIconsRow(
    chunk: List<AppIcon?>,
    selected: AppIcon,
    onAppIconSelect: (AppIcon) -> Unit,
    premium: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (i in 0 until 3) {
            val appIcon = chunk.getOrNull(i)
            if (appIcon != null) {
                IconBox(
                    appIcon.foreground,
                    appIcon.background,
                    appIcon.title.getString(),
                    appIcon == selected,
                    premium,
                ) { onAppIconSelect(appIcon) }
            } else {
                Spacer(modifier = Modifier.size(60.dp))
            }
        }
    }
}

@Composable
private fun IconBox(
    foreground: Int,
    background: Int,
    name: String,
    selected: Boolean,
    premium: Boolean,
    onAppIconSelect: () -> Unit
) {
    val iconCornerShape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onAppIconSelect
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .then(
                    if (selected) {
                        Modifier
                            .clip(iconCornerShape)
                            .border(2.dp, ComposeAppTheme.colors.jacob, iconCornerShape)
                    } else {
                        Modifier
                    }
                )
                .clip(iconCornerShape)
        ) {
            Image(
                painter = painterResource(background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Image(
                painter = painterResource(foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(Modifier.height(6.dp))
        if (selected || premium) {
            subhead1_jacob(name)
        } else {
            subhead1_leah(name)
        }
    }
}

@Preview
@Composable
private fun AppIconScreenPreview() {
    ComposeAppTheme {
        AppIconScreen(
            appIconOptions = Select(AppIcon.Main, AppIcon.entries),
            onAppIconSelect = {},
            onClose = {}
        )
    }
}
