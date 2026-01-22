package cash.p.terminal.network.data

interface AppHeadersProvider {
    val appVersion: String
    val currentLanguage: String
    val appSignature: String?
}
