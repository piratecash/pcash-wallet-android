package cash.p.terminal.modules.mnemonic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import cash.p.terminal.R
import cash.p.terminal.core.displayNameStringRes
import cash.p.terminal.modules.settings.checklistterms.GeneralTermsContent
import io.horizontalsystems.hdwalletkit.Language

@Composable
internal fun NonEnglishMnemonicTermsScreen(
    language: Language,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    GeneralTermsContent(
        termsStrings = stringArrayResource(R.array.non_english_mnemonic_terms_checkboxes).toList(),
        title = stringResource(R.string.non_english_mnemonic_terms_title),
        confirmButtonText = stringResource(R.string.Button_IAgree),
        onClose = onBack,
        onConfirm = onConfirm,
        warning = nonEnglishMnemonicTermsWarning(language),
        warningTitle = stringResource(R.string.non_english_mnemonic_terms_warning_title),
        onBack = onBack,
        onCancel = onBack,
        cancelButtonText = stringResource(R.string.Button_Cancel)
    )
}

@Composable
private fun nonEnglishMnemonicTermsWarning(language: Language): AnnotatedString {
    val languageName = stringResource(language.displayNameStringRes)
    val unsupportedPhrase = stringResource(
        R.string.non_english_mnemonic_terms_warning_unsupported_bold
    )
    val bip39Language = "BIP39 $languageName"
    val warning = stringResource(
        R.string.non_english_mnemonic_terms_warning,
        languageName,
        languageName
    )

    return remember(warning, languageName, unsupportedPhrase, bip39Language) {
        buildAnnotatedString {
            append(warning)
            addBoldStyleToAll(warning, languageName)
            addBoldStyleToAll(warning, unsupportedPhrase)
            addBoldStyleToAll(warning, bip39Language)
        }
    }
}

private fun AnnotatedString.Builder.addBoldStyleToAll(text: String, value: String) {
    var startIndex = text.indexOf(value)
    while (startIndex >= 0) {
        addStyle(
            style = SpanStyle(fontWeight = FontWeight.Bold),
            start = startIndex,
            end = startIndex + value.length
        )
        startIndex = text.indexOf(value, startIndex + value.length)
    }
}
