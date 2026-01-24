package cash.p.terminal.feature.miniapp.domain.storage

interface IUniqueCodeStorage {
    var uniqueCode: String
    var connectedAccountId: String
    var connectedEvmAddress: String
    var connectedEndpoint: String
    var cachedBalance: String
}
