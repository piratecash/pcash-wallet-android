package cash.p.terminal.network.unstoppable.data.entity

import cash.p.terminal.network.data.serializers.FlexibleBigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

// A single route. From /rate it is economics-only; from /swap it additionally carries an
// `execution` block and a top-level `uuid` tracking handle.
@Serializable
internal data class RouteDto(
    @Serializable(with = FlexibleBigDecimalSerializer::class)
    val expectedBuyAmount: BigDecimal? = null,
    // The enforced floor the route can deliver. An explicit `null` means the amount is only
    // an estimate (floating-rate P2P, re-priced at deposit) — no guarantee.
    @Serializable(with = FlexibleBigDecimalSerializer::class)
    val minBuyAmount: BigDecimal? = null,
    val estimatedTime: EstimatedTimeDto? = null,
    // EVM ERC20 spender to approve before swapping (Barter/Circle). On a rate route it is
    // top-level; on a committed route it can also ride execution.approval.spender.
    val approvalSpender: String? = null,
    // Present only on a committed (/v2/swap) route — tells you how to send funds.
    val execution: ExecutionDto? = null,
    // v2 tracking handle (swap_records.uuid), top-level on the committed response.
    val uuid: String? = null,
)

@Serializable
internal data class EstimatedTimeDto(
    val total: Long,
)
