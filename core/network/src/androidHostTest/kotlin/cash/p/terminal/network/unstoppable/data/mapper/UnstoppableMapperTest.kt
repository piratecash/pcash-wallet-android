package cash.p.terminal.network.unstoppable.data.mapper

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.unstoppable.data.entity.ApprovalDto
import cash.p.terminal.network.unstoppable.data.entity.ExecutionDto
import cash.p.terminal.network.unstoppable.data.entity.RouteDto
import cash.p.terminal.network.unstoppable.data.entity.SignableTxDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class UnstoppableMapperTest {

    private val mapper = UnstoppableMapper()

    @Test
    fun mapTrackStatus_allKnownStatuses_mapToExpectedEnum() {
        val expected = mapOf(
            "not_started" to TransactionStatusEnum.WAITING,
            "pending" to TransactionStatusEnum.CONFIRMING,
            "swapping" to TransactionStatusEnum.EXCHANGING,
            "completed" to TransactionStatusEnum.FINISHED,
            "refunded" to TransactionStatusEnum.REFUNDED,
            "failed" to TransactionStatusEnum.FAILED,
            // Non-terminal in-progress: the swap can still auto-resolve to refunded/completed,
            // so it must NOT map to FAILED.
            "action_required" to TransactionStatusEnum.WAITING,
        )

        expected.forEach { (status, expectedEnum) ->
            assertEquals("status=$status", expectedEnum, mapper.mapTrackStatus(status))
        }
    }

    @Test
    fun mapTrackStatus_unrecognizedStatus_returnsNull() {
        assertNull(mapper.mapTrackStatus("unknown"))
        assertNull(mapper.mapTrackStatus("some_future_status"))
    }

    @Test
    fun mapRoute_executionDto_carriesFieldsThroughAndKeepsMinBuyAmountNull() {
        val dto = RouteDto(
            expectedBuyAmount = BigDecimal("100"),
            minBuyAmount = null,
            execution = ExecutionDto(
                method = "signed_transaction",
                chain = "1",
                transactions = listOf(
                    SignableTxDto(kind = "evm", to = "0xTo", from = "0xFrom", value = "0x0", data = "0xabcdef", gas = "0x5208"),
                ),
                approval = ApprovalDto(spender = "0xSpender"),
            ),
            uuid = "uuid-1",
        )

        val route = mapper.mapRoute(dto)

        assertNull(route.minBuyAmount)
        val execution = requireNotNull(route.execution)
        assertEquals("1", execution.chain)
        val tx = execution.transactions.single()
        assertEquals("0xTo", tx.to)
        assertEquals("0xFrom", tx.from)
        assertEquals("0x0", tx.value)
        assertEquals("0xabcdef", tx.data)
        assertEquals("0x5208", tx.gas)
        assertEquals("0xSpender", execution.approval?.spender)
    }
}
