package cash.p.terminal.modules.declinedtokens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.managers.DeclinedToken
import cash.p.terminal.core.managers.normalizedTokenLabel
import cash.p.terminal.modules.backuplocal.fullbackup.WalletDeclinedTokens
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.ui.extensions.BottomSheetSelectorMultiple
import cash.p.terminal.ui.extensions.BottomSheetSelectorMultipleDialog
import cash.p.terminal.ui.extensions.BottomSheetSelectorViewItem
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.title

private const val DISPLAY_TEXT_MAX_LENGTH = 24
private const val MAX_ENUMERATED_TOKENS = 5

/** Source is untrusted: strip bidi/control chars and cap the length. */
private fun String.toDisplayText(): String =
    normalizedTokenLabel().take(DISPLAY_TEXT_MAX_LENGTH)

/** Display-only name, sanitized and truncated — never persisted. */
private fun DeclinedToken.displayName(): String =
    coinName.toDisplayText().ifEmpty { coinCode.toDisplayText() }.ifEmpty { tokenQueryId.toDisplayText() }

/** Display-only code, sanitized and truncated — never persisted. */
private fun DeclinedToken.displayCode(): String =
    coinCode.toDisplayText().ifEmpty { tokenQueryId.toDisplayText() }

/** Shows the identity that actually decides the contract/scale — the name above may claim anything. */
private fun DeclinedToken.displaySubtitle(decimalsLabel: String): String {
    val query = TokenQuery.fromId(tokenQueryId)
    val reference = query?.tokenType?.id?.substringAfterLast(':', "").orEmpty().normalizedTokenLabel()
    return listOfNotNull(
        displayCode(),
        query?.blockchainType?.title?.toDisplayText(),
        reference.takeIf { it.isNotEmpty() }?.shorten(),
        decimals?.let { "$decimalsLabel $it" },
    ).joinToString(" · ")
}

@Composable
private fun walletSubtitle(wallet: WalletDeclinedTokens): String =
    stringResource(R.string.declined_tokens_wallet, wallet.accountName.toDisplayText())

@Composable
private fun enumerationText(wallets: List<WalletDeclinedTokens>): String {
    val tokens = wallets.flatMap { it.tokens }
    return if (tokens.size > MAX_ENUMERATED_TOKENS) {
        pluralStringResource(R.plurals.declined_tokens_count, tokens.size, tokens.size)
    } else {
        tokens.joinToString(", ") { it.displayName() }
    }
}

private fun List<WalletDeclinedTokens>.flatTokens(): List<Pair<WalletDeclinedTokens, DeclinedToken>> =
    flatMap { wallet -> wallet.tokens.map { wallet to it } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeclinedTokensInfoBottomSheet(
    wallets: List<WalletDeclinedTokens>,
    onReview: () -> Unit,
    onAddAll: () -> Unit,
    onSkipAll: () -> Unit,
    onClose: () -> Unit,
) {
    TransparentModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.ic_attention_24),
            title = stringResource(R.string.declined_tokens_title),
            onCloseClick = onClose,
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
        ) {
            InfoText(text = stringResource(R.string.declined_tokens_description, enumerationText(wallets)))
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                text = stringResource(R.string.declined_tokens_warning),
            )
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp),
                title = stringResource(R.string.declined_tokens_select),
                onClick = onReview,
            )
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                title = stringResource(R.string.declined_tokens_add_all),
                onClick = onAddAll,
            )
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                title = stringResource(R.string.declined_tokens_skip_all),
                onClick = onSkipAll,
            )
            VSpacer(20.dp)
        }
    }
}

/**
 * [selectedIndexes] is deliberately NOT a `remember` key: re-keying on it would discard the
 * restored selection it's meant to seed.
 */
@Composable
private fun rememberDeclinedTokensSelectorConfig(
    wallets: List<WalletDeclinedTokens>,
    selectedIndexes: List<Int>,
): BottomSheetSelectorMultipleDialog.Config {
    val title = stringResource(R.string.declined_tokens_select_title)
    val description = stringResource(R.string.declined_tokens_warning)
    val decimalsLabel = stringResource(R.string.AddToken_Decimals)
    val walletHeaders = if (wallets.size > 1) wallets.associate { it.accountId to walletSubtitle(it) } else null

    return remember(wallets) {
        val flatTokens = wallets.flatTokens()
        BottomSheetSelectorMultipleDialog.Config(
            icon = ImageSource.Local(R.drawable.ic_attention_24),
            title = title,
            selectedIndexes = selectedIndexes,
            viewItems = flatTokens.mapIndexed { index, (wallet, token) ->
                val isFirstOfWallet = index == 0 || flatTokens[index - 1].first.accountId != wallet.accountId
                BottomSheetSelectorViewItem(
                    title = token.displayName(),
                    subtitle = token.displaySubtitle(decimalsLabel),
                    // Tapping the row copies the full identity, so it can be checked in an explorer.
                    copyableString = token.tokenQueryId,
                    header = if (isFirstOfWallet) walletHeaders?.get(wallet.accountId) else null,
                )
            },
            description = description,
            allowEmpty = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeclinedTokensSelectBottomSheet(
    wallets: List<WalletDeclinedTokens>,
    onConfirm: (Map<String, Set<String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Survives a locale/theme/font-scale change, which drops everything the selector remembers.
    var selectedIndexes by rememberSaveable(wallets) { mutableStateOf(emptyList<Int>()) }
    val config = rememberDeclinedTokensSelectorConfig(wallets, selectedIndexes)
    var completed by remember(wallets) { mutableStateOf(false) }

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BottomSheetSelectorMultiple(
            config = config,
            onItemsSelected = { indexes ->
                completed = true
                val flatTokens = wallets.flatTokens()
                val approvals = indexes.mapNotNull { flatTokens.getOrNull(it) }
                    .groupBy({ (wallet, _) -> wallet.accountId }, { (_, token) -> token.tokenQueryId })
                    .mapValues { it.value.toSet() }
                onConfirm(approvals)
            },
            // Also fires right after a successful selection, so only treat it as cancel if Done didn't fire first.
            onCloseClick = { if (!completed) onDismiss() },
            maxListHeight = 320.dp,
            onSelectionChange = { selectedIndexes = it },
        )
    }
}

/** Wires the review sheets to [host] so the callback plumbing exists once, not per screen. */
@Composable
fun DeclinedTokensSheets(host: DeclinedTokensReviewHost) {
    val review = host.tokenReview ?: return
    DeclinedTokensSheets(
        review = review,
        onReview = host::onReviewTokens,
        onApprove = host::onApproveTokens,
        onDismiss = host::onDismissTokenReview,
    )
}

@Composable
fun DeclinedTokensSheets(
    review: DeclinedTokensReview,
    onReview: () -> Unit,
    onApprove: (Map<String, Set<String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    when (review.stage) {
        DeclinedTokensStage.Info -> DeclinedTokensInfoBottomSheet(
            wallets = review.wallets,
            onReview = onReview,
            onAddAll = { onApprove(review.allTokenIds) },
            onSkipAll = { onApprove(emptyMap()) },
            onClose = onDismiss,
        )

        DeclinedTokensStage.Select -> DeclinedTokensSelectBottomSheet(
            wallets = review.wallets,
            onConfirm = onApprove,
            onDismiss = onDismiss,
        )
    }
}

private fun previewWallet(tokenCount: Int, accountName: String = "Main Wallet"): WalletDeclinedTokens =
    WalletDeclinedTokens(
        accountId = accountName,
        accountName = accountName,
        tokens = (1..tokenCount).map { index ->
            DeclinedToken(tokenQueryId = "token-$index", coinName = "Token $index", coinCode = "TK$index")
        },
    )

@Preview
@Composable
private fun DeclinedTokensInfoBottomSheetPreview() {
    ComposeAppTheme {
        DeclinedTokensInfoBottomSheet(
            wallets = listOf(previewWallet(3)),
            onReview = {},
            onAddAll = {},
            onSkipAll = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun DeclinedTokensInfoBottomSheetTwoWalletsPreview() {
    ComposeAppTheme {
        DeclinedTokensInfoBottomSheet(
            wallets = listOf(previewWallet(2), previewWallet(3, accountName = "Second Wallet")),
            onReview = {},
            onAddAll = {},
            onSkipAll = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun DeclinedTokensInfoBottomSheetLongListPreview() {
    ComposeAppTheme {
        DeclinedTokensInfoBottomSheet(
            wallets = listOf(previewWallet(8)),
            onReview = {},
            onAddAll = {},
            onSkipAll = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun DeclinedTokensSelectBottomSheetPreview() {
    ComposeAppTheme {
        DeclinedTokensSelectBottomSheet(
            wallets = listOf(previewWallet(4)),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun DeclinedTokensSelectBottomSheetTwoWalletsPreview() {
    ComposeAppTheme {
        DeclinedTokensSelectBottomSheet(
            wallets = listOf(previewWallet(2), previewWallet(3, accountName = "Second Wallet")),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun DeclinedTokensSheetsPreview() {
    ComposeAppTheme {
        DeclinedTokensSheets(
            review = DeclinedTokensReview(
                listOf(previewWallet(2), previewWallet(3, accountName = "Second Wallet"))
            ),
            onReview = {},
            onApprove = {},
            onDismiss = {},
        )
    }
}
