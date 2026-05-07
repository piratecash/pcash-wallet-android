package cash.p.terminal.modules.mnemonic

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.displayNameStringRes
import cash.p.terminal.ui.compose.components.SelectorDialogCompose
import cash.p.terminal.ui.compose.components.SelectorItem
import cash.p.terminal.ui_compose.components.B2
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead1_grey50
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.hdwalletkit.Language

internal val mnemonicLanguagesOrdered: List<Language> = listOf(
    Language.English,
    Language.Japanese,
    Language.Spanish,
    Language.SimplifiedChinese,
    Language.TraditionalChinese,
    Language.French,
    Language.Italian,
    Language.Korean,
    Language.Czech,
    Language.Portuguese,
)

@Composable
internal fun MnemonicLanguageSelectorDialog(
    languages: List<Language>,
    selectedLanguage: Language,
    onDismissRequest: () -> Unit,
    onSelectLanguage: (Language) -> Unit
) {
    SelectorDialogCompose(
        title = stringResource(R.string.CreateWallet_Wordlist),
        items = languages.map {
            SelectorItem(
                stringResource(it.displayNameStringRes),
                it == selectedLanguage,
                it
            )
        },
        onDismissRequest = onDismissRequest,
        onSelectItem = onSelectLanguage
    )
}

@Composable
internal fun MnemonicLanguageCell(
    language: Language,
    showLanguageSelectorDialog: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    RowUniversal(
        modifier = modifier.padding(horizontal = 16.dp),
        onClick = if (enabled) showLanguageSelectorDialog else null
    ) {
        val iconTint = if (enabled) ComposeAppTheme.colors.grey else ComposeAppTheme.colors.grey50
        Icon(
            painter = painterResource(id = R.drawable.ic_globe_20),
            contentDescription = null,
            tint = iconTint
        )
        B2(
            text = stringResource(R.string.CreateWallet_Wordlist),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.weight(1f))
        if (enabled) {
            subhead1_grey(
                text = stringResource(language.displayNameStringRes),
            )
        } else {
            subhead1_grey50(
                text = stringResource(language.displayNameStringRes),
            )
        }
        Icon(
            modifier = Modifier.padding(start = 4.dp),
            painter = painterResource(id = R.drawable.ic_down_arrow_20),
            contentDescription = null,
            tint = iconTint
        )
    }
}
