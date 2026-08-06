package cash.p.terminal.core

enum class MoneroSpendReadiness {
    Syncing,
    CheckingKeyImages,
    NeedsKeyImageSync,
    Ready,
}
