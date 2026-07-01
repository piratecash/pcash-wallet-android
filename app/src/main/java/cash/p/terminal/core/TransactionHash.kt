package cash.p.terminal.core

fun String.canonicalTransactionHash(): String =
    trim().lowercase().removePrefix("0x")

fun String.evmExplorerTransactionHash(): String {
    val hash = canonicalTransactionHash()
    return if (hash.isBlank()) hash else "0x$hash"
}
