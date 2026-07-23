package cash.p.terminal.network.unstoppable.domain.entity

import java.math.BigDecimal

/**
 * A quoted route for one sub-provider. `execution`/`uuid` are present only after `/v2/swap`
 * commits the order — a `/v2/rate` route carries economics only.
 */
data class UnstoppableRoute(
    val expectedBuyAmount: BigDecimal?,
    val minBuyAmount: BigDecimal?,
    val estimatedTimeSeconds: Long?,
    val approvalSpender: String?,
    val execution: UnstoppableExecution?,
    val uuid: String?,
) {
    /** EVM ERC20 spender to approve, whichever level of the response carries it. */
    val resolvedApprovalSpender: String?
        get() = execution?.approvalSpender ?: approvalSpender
}
