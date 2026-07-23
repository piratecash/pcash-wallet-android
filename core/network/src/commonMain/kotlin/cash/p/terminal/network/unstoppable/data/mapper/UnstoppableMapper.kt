package cash.p.terminal.network.unstoppable.data.mapper

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.unstoppable.data.entity.ApprovalDto
import cash.p.terminal.network.unstoppable.data.entity.AttachmentDto
import cash.p.terminal.network.unstoppable.data.entity.ExecutionDto
import cash.p.terminal.network.unstoppable.data.entity.ProviderDto
import cash.p.terminal.network.unstoppable.data.entity.RouteDto
import cash.p.terminal.network.unstoppable.data.entity.SignableTxDto
import cash.p.terminal.network.unstoppable.data.entity.TokensDto
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableApproval
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableAttachment
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableExecution
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderContacts
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderInfo
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderTokens
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableRoute
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableSignableTx
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableToken

internal class UnstoppableMapper {
    fun mapRoute(dto: RouteDto) = UnstoppableRoute(
        expectedBuyAmount = dto.expectedBuyAmount,
        minBuyAmount = dto.minBuyAmount,
        estimatedTimeSeconds = dto.estimatedTime?.total,
        approvalSpender = dto.approvalSpender,
        execution = dto.execution?.let(::mapExecution),
        uuid = dto.uuid,
    )

    fun mapTokens(dto: TokensDto) = UnstoppableProviderTokens(
        tokens = dto.tokens.map { token ->
            UnstoppableToken(
                chain = token.chain,
                chainId = token.chainId,
                address = token.address,
                identifier = token.identifier,
            )
        },
        supportedChainIds = dto.supportedChainIds,
    )

    fun mapProvider(dto: ProviderDto) = UnstoppableProviderInfo(
        provider = dto.provider,
        name = dto.name,
        supportedChainIds = dto.supportedChainIds,
        amlPolicy = dto.amlPolicy,
        amlPolicyDescription = dto.amlPolicyDescription,
        contacts = dto.contacts?.let { contacts ->
            UnstoppableProviderContacts(
                email = contacts.email,
                telegram = contacts.telegram,
                twitter = contacts.twitter,
                website = contacts.website,
            )
        },
    )

    /**
     * `not_started`/`action_required` map to WAITING — `action_required` is treated as
     * non-terminal in-progress (the swap can still auto-resolve to refunded/completed).
     * `unknown`/unrecognized returns `null` so a caller never persists a bogus status.
     */
    fun mapTrackStatus(status: String): TransactionStatusEnum? = when (status) {
        "not_started" -> TransactionStatusEnum.WAITING
        "pending" -> TransactionStatusEnum.CONFIRMING
        "swapping" -> TransactionStatusEnum.EXCHANGING
        "completed" -> TransactionStatusEnum.FINISHED
        "refunded" -> TransactionStatusEnum.REFUNDED
        "failed" -> TransactionStatusEnum.FAILED
        "action_required" -> TransactionStatusEnum.WAITING
        else -> null
    }

    private fun mapExecution(dto: ExecutionDto) = UnstoppableExecution(
        method = dto.method,
        chain = dto.chain,
        transactions = dto.transactions.orEmpty().map(::mapSignableTx),
        approval = dto.approval?.let(::mapApproval),
        depositAddress = dto.depositAddress,
        attachment = dto.attachment?.let(::mapAttachment),
        unsignedTx = dto.unsignedTx?.let(::mapSignableTx),
    )

    private fun mapSignableTx(dto: SignableTxDto) = UnstoppableSignableTx(
        kind = dto.kind,
        to = dto.to,
        from = dto.from,
        value = dto.value,
        data = dto.data,
        gas = dto.gas,
    )

    private fun mapApproval(dto: ApprovalDto) = UnstoppableApproval(
        token = dto.token,
        spender = dto.spender,
        amount = dto.amount,
    )

    private fun mapAttachment(dto: AttachmentDto) = UnstoppableAttachment(
        type = dto.type,
        value = dto.value,
    )
}
