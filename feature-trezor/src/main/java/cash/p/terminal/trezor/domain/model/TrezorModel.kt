package cash.p.terminal.trezor.domain.model

enum class TrezorModel(val id: String, val displayName: String) {
    One("T1B1", "Trezor One"),
    ModelT("T2T1", "Trezor Model T"),
    Safe3("T3B1", "Trezor Safe 3"),
    Safe5("T3T1", "Trezor Safe 5"),
    Safe7("T3W1", "Trezor Safe 7");

    companion object {
        fun fromInternalModel(internalModel: String?): TrezorModel? =
            entries.find { it.id == internalModel }
    }
}
