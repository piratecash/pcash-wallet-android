package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ThorChainSwapStatusRepositoryTest {

    private val destinationAddress = "thor1destination"

    @Test
    fun mapTxStatus_noStages_returnsWaiting() {
        val txStatus = ThornodeAPI.Response.TxStatus(
            stages = null,
            outTxs = null,
            plannedOutTxs = null,
        )

        val result = mapTxStatus(txStatus, destinationAddress)

        assertEquals(TransactionStatusEnum.WAITING, result.status)
        assertNull(result.finishedAt)
    }

    @Test
    fun mapTxStatus_inboundObservedOnly_returnsConfirming() {
        val txStatus = ThornodeAPI.Response.TxStatus(
            stages = ThornodeAPI.Response.TxStatus.Stages(
                inboundObserved = ThornodeAPI.Response.TxStatus.StageObserved(completed = true),
                inboundConfirmationCounted = null,
                inboundFinalised = ThornodeAPI.Response.TxStatus.StageCompleted(completed = false),
                swapStatus = null,
                swapFinalised = null,
                outboundSigned = null,
            ),
            outTxs = null,
            plannedOutTxs = null,
        )

        val result = mapTxStatus(txStatus, destinationAddress)

        assertEquals(TransactionStatusEnum.CONFIRMING, result.status)
        assertNull(result.finishedAt)
    }

    @Test
    fun mapTxStatus_inboundFinalisedSwapPending_returnsExchanging() {
        val txStatus = ThornodeAPI.Response.TxStatus(
            stages = ThornodeAPI.Response.TxStatus.Stages(
                inboundObserved = ThornodeAPI.Response.TxStatus.StageObserved(completed = true),
                inboundConfirmationCounted = null,
                inboundFinalised = ThornodeAPI.Response.TxStatus.StageCompleted(completed = true),
                swapStatus = ThornodeAPI.Response.TxStatus.SwapStatus(pending = true),
                swapFinalised = ThornodeAPI.Response.TxStatus.StageCompleted(completed = false),
                outboundSigned = null,
            ),
            outTxs = null,
            plannedOutTxs = null,
        )

        val result = mapTxStatus(txStatus, destinationAddress)

        assertEquals(TransactionStatusEnum.EXCHANGING, result.status)
        assertNull(result.finishedAt)
    }

    @Test
    fun mapTxStatus_swapFinalisedOutboundNotSigned_returnsSending() {
        val txStatus = ThornodeAPI.Response.TxStatus(
            stages = ThornodeAPI.Response.TxStatus.Stages(
                inboundObserved = ThornodeAPI.Response.TxStatus.StageObserved(completed = true),
                inboundConfirmationCounted = null,
                inboundFinalised = ThornodeAPI.Response.TxStatus.StageCompleted(completed = true),
                swapStatus = ThornodeAPI.Response.TxStatus.SwapStatus(pending = false),
                swapFinalised = ThornodeAPI.Response.TxStatus.StageCompleted(completed = true),
                outboundSigned = ThornodeAPI.Response.TxStatus.StageCompleted(completed = false),
            ),
            outTxs = null,
            plannedOutTxs = null,
        )

        val result = mapTxStatus(txStatus, destinationAddress)

        assertEquals(TransactionStatusEnum.SENDING, result.status)
        assertNull(result.finishedAt)
    }

    @Test
    fun mapTxStatus_outboundSigned_returnsFinishedWithAmountAndFinishedAt() {
        val txStatus = ThornodeAPI.Response.TxStatus(
            stages = ThornodeAPI.Response.TxStatus.Stages(
                inboundObserved = ThornodeAPI.Response.TxStatus.StageObserved(completed = true),
                inboundConfirmationCounted = null,
                inboundFinalised = ThornodeAPI.Response.TxStatus.StageCompleted(completed = true),
                swapStatus = ThornodeAPI.Response.TxStatus.SwapStatus(pending = false),
                swapFinalised = ThornodeAPI.Response.TxStatus.StageCompleted(completed = true),
                outboundSigned = ThornodeAPI.Response.TxStatus.StageCompleted(completed = true),
            ),
            outTxs = listOf(
                ThornodeAPI.Response.TxStatus.OutTx(
                    chain = "ETH",
                    toAddress = destinationAddress,
                    coins = listOf(
                        ThornodeAPI.Response.TxStatus.Coin(asset = "ETH.ETH", amount = BigDecimal(100_000_000)),
                    ),
                ),
            ),
            plannedOutTxs = null,
        )

        val result = mapTxStatus(txStatus, destinationAddress)

        assertEquals(TransactionStatusEnum.FINISHED, result.status)
        assertEquals(BigDecimal(1).setScale(8), result.amountOutReal?.setScale(8))
        assertNotNull(result.finishedAt)
    }

    @Test
    fun mapTxStatus_refundFlagWithOutboundSigned_returnsRefunded() {
        val txStatus = ThornodeAPI.Response.TxStatus(
            stages = ThornodeAPI.Response.TxStatus.Stages(
                inboundObserved = ThornodeAPI.Response.TxStatus.StageObserved(completed = true),
                inboundConfirmationCounted = null,
                inboundFinalised = ThornodeAPI.Response.TxStatus.StageCompleted(completed = true),
                swapStatus = ThornodeAPI.Response.TxStatus.SwapStatus(pending = false),
                swapFinalised = ThornodeAPI.Response.TxStatus.StageCompleted(completed = true),
                outboundSigned = ThornodeAPI.Response.TxStatus.StageCompleted(completed = true),
            ),
            outTxs = null,
            plannedOutTxs = listOf(
                ThornodeAPI.Response.TxStatus.PlannedOutTx(
                    refund = true,
                    chain = "ETH",
                    toAddress = "thor1sender",
                    coin = ThornodeAPI.Response.TxStatus.Coin(asset = "ETH.ETH", amount = BigDecimal(50_000_000)),
                ),
            ),
        )

        val result = mapTxStatus(txStatus, destinationAddress)

        assertEquals(TransactionStatusEnum.REFUNDED, result.status)
        assertNotNull(result.finishedAt)
    }
}
