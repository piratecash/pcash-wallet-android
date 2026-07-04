package cash.p.terminal.modules.multiswap

import cash.p.terminal.modules.send.hasInsufficientFeeTokenBalance
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SwapConfirmScreenTest {

    @Test
    fun isSwapConfirmButtonEnabled_validState_returnsTrue() {
        assertTrue(
            isSwapConfirmButtonEnabled(
                isSynced = true,
                swapInProgress = false,
                hasRequiredQuoteData = true,
                hasBlockingFeeState = false,
                hasErrorCaution = false,
            )
        )
    }

    @Test
    fun isSwapConfirmButtonEnabled_insufficientFeeBalance_returnsFalse() {
        assertFalse(
            isSwapConfirmButtonEnabled(
                isSynced = true,
                swapInProgress = false,
                hasRequiredQuoteData = true,
                hasBlockingFeeState = true,
                hasErrorCaution = false,
            )
        )
    }

    @Test
    fun hasSwapConfirmFeeProblem_feeCaution_returnsTrue() {
        assertTrue(
            hasSwapConfirmFeeProblem(
                hasInsufficientFeeBalance = false,
                hasFeeCaution = true,
            )
        )
    }

    @Test
    fun hasInsufficientFeeTokenBalance_splFeeExceedsNativeBalance_returnsTrue() {
        val token = token(TokenType.Spl("spl-address"))

        assertTrue(
            hasInsufficientFeeTokenBalance(
                token = token,
                fee = BigDecimal("0.000155"),
                feeTokenBalance = BigDecimal.ZERO,
            )
        )
    }

    private fun token(type: TokenType) = Token(
        coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
        blockchain = Blockchain(
            type = BlockchainType.Solana,
            name = "Solana",
            eip3091url = null
        ),
        type = type,
        decimals = 6
    )
}
