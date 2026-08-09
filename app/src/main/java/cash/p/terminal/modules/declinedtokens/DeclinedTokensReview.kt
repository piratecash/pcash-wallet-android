package cash.p.terminal.modules.declinedtokens

import cash.p.terminal.modules.backuplocal.fullbackup.WalletDeclinedTokens

enum class DeclinedTokensStage { Info, Select }

/** [tokenReview] lives here, not in a screen, so reading review state doesn't recompose the screen. */
interface DeclinedTokensReviewHost {
    val tokenReview: DeclinedTokensReview?

    fun onReviewTokens()
    fun onApproveTokens(approvals: Map<String, Set<String>>)
    fun onDismissTokenReview()
}

data class DeclinedTokensReview(
    val wallets: List<WalletDeclinedTokens>,
    val stage: DeclinedTokensStage = DeclinedTokensStage.Info,
) {
    val allTokenIds: Map<String, Set<String>>
        get() = wallets.associate { it.accountId to it.tokens.mapTo(mutableSetOf()) { token -> token.tokenQueryId } }
}
