package cash.p.terminal.trezor.domain.model

enum class TrezorModel(val ids: Set<String>, val displayName: String) {
    One(setOf("T1B1"), "Trezor One"),
    ModelT(setOf("T2T1"), "Trezor Model T"),
    // Trezor brands both the early (T2B1) and the newer (T3B1) generation as "Safe 3".
    Safe3(setOf("T2B1", "T3B1"), "Trezor Safe 3"),
    Safe5(setOf("T3T1"), "Trezor Safe 5"),
    Safe7(setOf("T3W1"), "Trezor Safe 7");

    companion object {
        fun fromInternalModel(internalModel: String?): TrezorModel? =
            internalModel?.let { id -> entries.find { id in it.ids } }
    }
}
