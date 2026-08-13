package cash.p.terminal.shared.main

enum class MainDestination {
    Balance,
    Transactions,
    Market,
    Settings;

    companion object {
        fun fromString(value: String?): MainDestination? = entries.firstOrNull { it.name == value }
    }
}
