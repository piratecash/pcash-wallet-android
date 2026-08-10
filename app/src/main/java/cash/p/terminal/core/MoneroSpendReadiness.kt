package cash.p.terminal.core

enum class MoneroSpendReadiness {
    Syncing,
    CheckingKeyImages,
    ReconcilingSpentStatus,
    ReconciliationFailed,
    NeedsKeyImageSync,
    Ready,
}

internal fun MoneroSpendReadiness.requiresTrezorPreparation(): Boolean =
    this == MoneroSpendReadiness.NeedsKeyImageSync ||
        this == MoneroSpendReadiness.ReconciliationFailed
