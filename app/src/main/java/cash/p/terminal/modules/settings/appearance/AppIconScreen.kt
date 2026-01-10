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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_jacob
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun AppIconScreen(
    appIconOptions: Select<AppIcon>,
    onAppIconSelect: (AppIcon) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedAppIcon by remember { mutableStateOf<AppIcon?>(null) }
    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetBackgroundColor = ComposeAppTheme.colors.transparent,
        sheetContent = {
            AppCloseWarningBottomSheet(
                onCloseClick = { scope.launch { sheetState.hide() } },
                onChangeClick = {
                    selectedAppIcon?.let { onAppIconSelect(it) }
                    scope.launch { sheetState.hide() }
                }
            )
        }
    ) {
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
                    scope.launch {
                        selectedAppIcon = appIcon
                        sheetState.show()
                    }
                }
                VSpacer(32.dp)
            }
        }
    }
}

@Composable
private fun AppCloseWarningBottomSheet(
    onCloseClick: () -> Unit,
    onChangeClick: () -> Unit
) {
    BottomSheetHeader(
        iconPainter = painterResource(id = R.drawable.ic_attention_24),
        title = stringResource(id = R.string.Alert_TitleWarning),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
        onCloseClick = onCloseClick
    ) {
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = stringResource(R.string.Appearance_Warning_CloseApplication)
        )

        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp),
            title = stringResource(id = R.string.Button_Change),
            onClick = onChangeClick
        )

        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            title = stringResource(id = R.string.Button_Cancel),
            onClick = onCloseClick
        )
        VSpacer(20.dp)
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
    val otherIcons = remember(appIconOptions.options) {
        appIconOptions.options.filter { it.category == AppIconCategory.OTHER }
    }

    HeaderText(text = stringResource(R.string.appearance_app_icon_our_icon))
    AppIconCard(ourIcons, appIconOptions.selected, onAppIconSelect)

    VSpacer(16.dp)

    HeaderText(text = stringResource(R.string.appearance_app_icon_other))
    AppIconCard(otherIcons, appIconOptions.selected, onAppIconSelect)
}

@Composable
private fun AppIconCard(
    icons: List<AppIcon>,
    selected: AppIcon,
    onAppIconSelect: (AppIcon) -> Unit
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
            AppIconsRow(row, selected, onAppIconSelect)
        }
    }
}

@Composable
private fun AppIconsRow(
    chunk: List<AppIcon?>,
    selected: AppIcon,
    onAppIconSelect: (AppIcon) -> Unit
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
                    appIcon == selected
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
        if (selected) {
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
