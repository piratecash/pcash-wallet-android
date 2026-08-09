package cash.p.terminal.modules.declinedtokens

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cash.p.terminal.core.managers.DeclinedToken
import cash.p.terminal.modules.backuplocal.fullbackup.WalletDeclinedTokens
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [cash.p.terminal.ui.extensions.BottomSheetSelectorMultiple] fires `onCloseClick` both on a real
 * cancel (X button) and right after a successful Done, so [DeclinedTokensSelectBottomSheet] must
 * tell those two cases apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeclinedTokensSheetsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val walletOne = WalletDeclinedTokens(
        accountId = "acc-1",
        accountName = "Wallet One",
        tokens = listOf(
            DeclinedToken(tokenQueryId = "token-a", coinName = "Token A", coinCode = "TKA"),
            DeclinedToken(tokenQueryId = "token-b", coinName = "Token B", coinCode = "TKB"),
        ),
    )

    private val walletTwo = WalletDeclinedTokens(
        accountId = "acc-2",
        accountName = "Wallet Two",
        tokens = listOf(
            DeclinedToken(tokenQueryId = "token-c", coinName = "Token C", coinCode = "TKC"),
            DeclinedToken(tokenQueryId = "token-d", coinName = "Token D", coinCode = "TKD"),
        ),
    )

    @Test
    fun infoSheet_selectClicked_callsOnReviewOnlyAndOnApproveZeroTimes() {
        val onReview: () -> Unit = mockk(relaxed = true)
        val onApprove: (Map<String, Set<String>>) -> Unit = mockk(relaxed = true)
        setInfoSheetContent(listOf(walletOne), onReview = onReview, onApprove = onApprove)

        composeTestRule.onNodeWithText("Select").performClick()

        verify(exactly = 1) { onReview() }
        verify(exactly = 0) { onApprove(any()) }
    }

    @Test
    fun infoSheet_addAllClicked_callsOnApproveWithAllTokenIds() {
        val onApprove: (Map<String, Set<String>>) -> Unit = mockk(relaxed = true)
        val review = DeclinedTokensReview(listOf(walletOne, walletTwo))
        setInfoSheetContent(review.wallets, onApprove = onApprove)

        composeTestRule.onNodeWithText("Add All").performClick()

        verify(exactly = 1) { onApprove(review.allTokenIds) }
    }

    @Test
    fun infoSheet_skipAllClicked_callsOnApproveWithEmptyMap() {
        val onApprove: (Map<String, Set<String>>) -> Unit = mockk(relaxed = true)
        setInfoSheetContent(listOf(walletOne, walletTwo), onApprove = onApprove)

        composeTestRule.onNodeWithText("Skip All").performClick()

        verify(exactly = 1) { onApprove(emptyMap()) }
    }

    @Test
    fun infoSheet_twoWallets_enumeratesTokensFromBothWallets() {
        setInfoSheetContent(listOf(walletOne, walletTwo))

        composeTestRule.onNodeWithText("Token A", substring = true).assertExists()
        composeTestRule.onNodeWithText("Token C", substring = true).assertExists()
    }

    @Test
    fun infoSheet_tokenNameWithNewlineAndBidiOverride_rendersSanitized() {
        val maliciousWallet = walletOne.copy(
            tokens = listOf(DeclinedToken(tokenQueryId = "token-a", coinName = "Bad‮TokenX\nY", coinCode = "BAD")),
        )
        setInfoSheetContent(listOf(maliciousWallet))

        composeTestRule.onNodeWithText("Bad TokenX Y", substring = true).assertExists()
        composeTestRule.onNodeWithText("‮", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("TokenX\nY", substring = true).assertDoesNotExist()
    }

    @Test
    fun selectSheet_doneWithoutSelection_firesOnConfirmWithEmptyMap() {
        val onConfirm: (Map<String, Set<String>>) -> Unit = mockk(relaxed = true)
        setSelectSheetContent(listOf(walletOne), onConfirm = onConfirm)

        composeTestRule.onNodeWithText("Done").performClick()

        verify(exactly = 1) { onConfirm(emptyMap()) }
    }

    @Test
    fun selectSheet_oneRowSwitchedOn_mapsToThatWalletsAccountId() {
        val onConfirm: (Map<String, Set<String>>) -> Unit = mockk(relaxed = true)
        setSelectSheetContent(listOf(walletOne), onConfirm = onConfirm)

        composeTestRule.onAllNodes(isToggleable())[0].performClick()
        composeTestRule.onNodeWithText("Done").performClick()

        verify(exactly = 1) { onConfirm(mapOf(walletOne.accountId to setOf("token-a"))) }
    }

    @Test
    fun selectSheet_closeWithoutDone_firesOnDismissOnceAndOnConfirmZeroTimes() {
        val onConfirm: (Map<String, Set<String>>) -> Unit = mockk(relaxed = true)
        val onDismiss: () -> Unit = mockk(relaxed = true)
        setSelectSheetContent(listOf(walletOne), onConfirm = onConfirm, onDismiss = onDismiss)

        composeTestRule.onNode(closeButtonMatcher).performClick()

        verify(exactly = 1) { onDismiss() }
        verify(exactly = 0) { onConfirm(any()) }
    }

    @Test
    fun selectSheet_twoWallets_rendersBothWalletHeaders() {
        setSelectSheetContent(listOf(walletOne, walletTwo))

        composeTestRule.onNodeWithText("Wallet: Wallet One", substring = true).assertExists()
        composeTestRule.onNodeWithText("Wallet: Wallet Two", substring = true).assertExists()
    }

    @Test
    fun selectSheet_singleWallet_rendersNoWalletHeader() {
        setSelectSheetContent(listOf(walletOne))

        composeTestRule.onNodeWithText("Wallet:", substring = true).assertDoesNotExist()
    }

    @Test
    fun selectSheet_rowsFromBothWalletsBoundarySelected_mapsFlatIndexesToPerAccountTokenIds() {
        val onConfirm: (Map<String, Set<String>>) -> Unit = mockk(relaxed = true)
        setSelectSheetContent(listOf(walletOne, walletTwo), onConfirm = onConfirm)

        // Index 1 is walletOne's last row, index 2 is walletTwo's first row — the seam of the
        // flat-index mapping. The list is height-capped, so each target must be scrolled into view.
        composeTestRule.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        composeTestRule.onAllNodes(isToggleable())[2].performScrollTo().performClick()
        composeTestRule.onNodeWithText("Done").performScrollTo().performClick()

        verify(exactly = 1) {
            onConfirm(mapOf(walletOne.accountId to setOf("token-b"), walletTwo.accountId to setOf("token-c")))
        }
    }

    /**
     * Regression test: `BottomSheetSelectorMultiple.onSelectionChange` must copy to a plain
     * `ArrayList` — `SnapshotStateList.toList()` can't be put in a Bundle and crashed on first save.
     */
    @Test
    fun selectSheet_checkedRowsSurviveStateRestoration_reportedOnDone() {
        val stateRestorationTester = StateRestorationTester(composeTestRule)
        val onConfirm: (Map<String, Set<String>>) -> Unit = mockk(relaxed = true)

        stateRestorationTester.setContent {
            ComposeAppTheme {
                DeclinedTokensSelectBottomSheet(
                    wallets = listOf(walletOne),
                    onConfirm = onConfirm,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onAllNodes(isToggleable())[0].performClick()
        stateRestorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithText("Done").performClick()

        verify(exactly = 1) { onConfirm(mapOf(walletOne.accountId to setOf("token-a"))) }
    }

    @Test
    fun selectSheet_tokenWithCanonicalQueryId_subtitleShowsChainContractAndDecimals() {
        val tokenQueryId = TokenQuery.eip20(BlockchainType.BinanceSmartChain, CONTRACT).id
        val identifiedWallet = walletOne.copy(
            tokens = listOf(
                DeclinedToken(tokenQueryId = tokenQueryId, coinName = "Token A", coinCode = "TKA", decimals = 18),
            ),
        )
        setSelectSheetContent(listOf(identifiedWallet))

        composeTestRule.onNodeWithText("BNB Smart Chain", substring = true).assertExists()
        composeTestRule.onNodeWithText(CONTRACT.take(12), substring = true).assertExists()
        composeTestRule.onNodeWithText("Decimals 18", substring = true).assertExists()
    }

    @Test
    fun selectSheet_contractReferenceWithBidiOverride_rendersSanitized() {
        val crafted = walletOne.copy(
            tokens = listOf(
                DeclinedToken(
                    tokenQueryId = TokenQuery.eip20(BlockchainType.Ethereum, "0x00‮evil").id,
                    coinName = "Token A",
                    coinCode = "TKA",
                ),
            ),
        )
        setSelectSheetContent(listOf(crafted))

        composeTestRule.onNodeWithText("‮", substring = true).assertDoesNotExist()
    }

    @Test
    fun selectSheet_accountNameWithBidiOverride_rendersSanitizedInHeader() {
        val crafted = walletTwo.copy(accountName = "Real‮Wallet")
        setSelectSheetContent(listOf(walletOne, crafted))

        composeTestRule.onNodeWithText("Wallet: Real Wallet", substring = true).assertExists()
        composeTestRule.onNodeWithText("‮", substring = true).assertDoesNotExist()
    }

    @Test
    fun declinedTokensSheets_hostOverloadSelectClicked_callsHostOnReviewTokens() {
        val host: DeclinedTokensReviewHost = mockk(relaxed = true) {
            every { tokenReview } returns DeclinedTokensReview(listOf(walletOne))
        }

        composeTestRule.setContent {
            ComposeAppTheme { DeclinedTokensSheets(host) }
        }

        composeTestRule.onNodeWithText("Select").performClick()

        verify(exactly = 1) { host.onReviewTokens() }
    }

    @Test
    fun declinedTokensSheets_hostOverloadNullReview_rendersNothing() {
        val host: DeclinedTokensReviewHost = mockk(relaxed = true) {
            every { tokenReview } returns null
        }

        composeTestRule.setContent {
            ComposeAppTheme { DeclinedTokensSheets(host) }
        }

        composeTestRule.onNodeWithText("Custom Tokens Found").assertDoesNotExist()
    }

    private fun setInfoSheetContent(
        wallets: List<WalletDeclinedTokens>,
        onReview: () -> Unit = {},
        onApprove: (Map<String, Set<String>>) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ComposeAppTheme {
                DeclinedTokensSheets(
                    review = DeclinedTokensReview(wallets),
                    onReview = onReview,
                    onApprove = onApprove,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    private fun setSelectSheetContent(
        wallets: List<WalletDeclinedTokens>,
        onConfirm: (Map<String, Set<String>>) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ComposeAppTheme {
                DeclinedTokensSelectBottomSheet(
                    wallets = wallets,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    private companion object {
        const val CONTRACT = "0x1234567890abcdef1234567890abcdef12345678"

        // The header's X icon is a Role.Button with no descendant text — unlike "Done", which
        // merges its Text child into the same semantics node.
        val closeButtonMatcher = SemanticsMatcher("close icon button") { node ->
            node.config.getOrNull(SemanticsProperties.Role) == Role.Button &&
                node.config.getOrNull(SemanticsProperties.Text) == null
        }
    }
}
