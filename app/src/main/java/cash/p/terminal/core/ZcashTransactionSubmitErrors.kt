package cash.p.terminal.core

internal fun String?.isZcashAlreadyCommittedToBestChainError(): Boolean {
    val message = this?.lowercase() ?: return false
    return message.contains("committed to the best chain") ||
            message.contains("same effects will be rejected from the mempool")
}
