package cash.p.terminal.core.address

import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.send.address.AddressCheckResult
import cash.p.terminal.wallet.Token

class AddressCheckManager(
    spamManager: SpamManager,
    evmBlockchainManager: EvmBlockchainManager,
    evmSyncSourceManager: EvmSyncSourceManager
) {
    private val checkers = mapOf(
        AddressCheckType.SmartContract to ContractAddressChecker(),
        AddressCheckType.Phishing to PhishingAddressChecker(spamManager),
        AddressCheckType.Blacklist to BlacklistAddressChecker(
            HashDitAddressValidator(
                AppConfigProvider.hashDitBaseUrl,
                AppConfigProvider.hashDitApiKey,
                evmBlockchainManager
            ),
            Eip20AddressValidator(evmSyncSourceManager)
        ),
        AddressCheckType.AmlCheck to AmlAddressChecker(
            AlphaAmlAddressValidator()
        ),
        AddressCheckType.Sanction to SanctionAddressChecker(
            ChainalysisAddressValidator(
                AppConfigProvider.chainalysisBaseUrl,
                AppConfigProvider.chainalysisApiKey
            )
        )
    )

    private val cache = mutableMapOf<CacheKey, AddressCheckResult>()

    fun availableCheckTypes(token: Token): List<AddressCheckType> {
        return checkers.mapNotNull { (type, checker) -> if (checker.supports(token)) type else null }
    }

    suspend fun isClear(type: AddressCheckType, address: Address, token: Token): AddressCheckResult {
        val key = CacheKey(type, address.hex, token)

        return cache[key] ?: run {
            checkers[type]?.isClear(address, token)?.also {
                cache[key] = it
            } ?: AddressCheckResult.Clear
        }
    }

    private data class CacheKey(
        val type: AddressCheckType,
        val address: String,
        val token: Token
    )
}
