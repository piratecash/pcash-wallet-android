package cash.p.terminal.modules.declinedtokens

import cash.p.terminal.core.managers.DeclinedToken
import cash.p.terminal.modules.backuplocal.fullbackup.WalletDeclinedTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class DeclinedTokensReviewTest {

    private fun token(id: String) = DeclinedToken(tokenQueryId = id, coinName = "Coin $id", coinCode = id)

    private fun wallet(accountId: String, vararg tokenIds: String) =
        WalletDeclinedTokens(accountId = accountId, accountName = "Wallet $accountId", tokens = tokenIds.map(::token))

    @Test
    fun allTokenIds_singleWallet_mapsAccountIdToItsTokenIds() {
        val review = DeclinedTokensReview(listOf(wallet("acc1", "token-1", "token-2")))

        assertEquals(mapOf("acc1" to setOf("token-1", "token-2")), review.allTokenIds)
    }

    @Test
    fun allTokenIds_severalWallets_keyedByEachWalletsAccountId() {
        val review = DeclinedTokensReview(listOf(wallet("acc1", "token-1"), wallet("acc2", "token-2", "token-3")))

        assertEquals(
            mapOf("acc1" to setOf("token-1"), "acc2" to setOf("token-2", "token-3")),
            review.allTokenIds
        )
    }

    @Test
    fun stage_defaultsToInfo() {
        val review = DeclinedTokensReview(listOf(wallet("acc1", "token-1")))

        assertEquals(DeclinedTokensStage.Info, review.stage)
    }

    @Test
    fun copy_withSelectStage_transitionsStageOnlyAndKeepsWallets() {
        val review = DeclinedTokensReview(listOf(wallet("acc1", "token-1")))

        val next = review.copy(stage = DeclinedTokensStage.Select)

        assertEquals(DeclinedTokensStage.Select, next.stage)
        assertEquals(review.wallets, next.wallets)
    }
}
