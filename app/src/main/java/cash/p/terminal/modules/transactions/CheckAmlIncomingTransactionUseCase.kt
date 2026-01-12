package cash.p.terminal.modules.transactions

import cash.p.terminal.core.address.AddressCheckManager
import cash.p.terminal.core.address.AddressCheckType
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.send.address.AddressCheckResult
import cash.p.terminal.wallet.Token
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber

data class AmlCheckResults(
    val perAddress: Map<String, IncomingAddressCheckResult>,
    val overall: IncomingAddressCheckResult
)

class CheckAmlIncomingTransactionUseCase(
    private val addressCheckManager: AddressCheckManager
) {
    private val checkTypes = listOf(
        AddressCheckType.Phishing,
        AddressCheckType.Blacklist,
        AddressCheckType.AmlCheck,
        AddressCheckType.Sanction
    )

    suspend operator fun invoke(
        addresses: List<Address>,
        token: Token
    ): AmlCheckResults {
        if (addresses.isEmpty()) {
            return AmlCheckResults(emptyMap(), IncomingAddressCheckResult.Unknown)
        }

        val supportedTypes = addressCheckManager.availableCheckTypes(token)
            .filter { it in checkTypes }

        if (supportedTypes.isEmpty()) {
            return AmlCheckResults(emptyMap(), IncomingAddressCheckResult.Unknown)
        }

        // Check each (address, type) pair and keep address association
        val resultsWithAddress = coroutineScope {
            addresses.flatMap { address ->
                supportedTypes.map { type ->
                    async {
                        address.hex to checkWithRetry(type, address, token).toIncomingResult()
                    }
                }
            }.awaitAll()
        }

        // Group by address and take worst result per address
        val perAddress = resultsWithAddress
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, results) ->
                results.maxByOrNull { it.ordinal } ?: IncomingAddressCheckResult.Unknown
            }

        // Overall worst across all addresses
        val overall = perAddress.values
            .maxByOrNull { it.ordinal }
            ?: IncomingAddressCheckResult.Unknown

        return AmlCheckResults(perAddress, overall)
    }

    private suspend fun checkWithRetry(
        type: AddressCheckType,
        address: Address,
        token: Token,
        maxRetries: Int = 3
    ): AddressCheckResult {
        repeat(maxRetries) { attempt ->
            try {
                return addressCheckManager.isClear(type, address, token)
            } catch (e: Throwable) {
                Timber.d("CheckAmlIncomingTransactionUseCase: Address check failed for type $type, address ${address.hex}, attempt $attempt: ${e.message}")
                if (attempt < maxRetries - 1) {
                    delay(1000)
                }
            }
        }
        return AddressCheckResult.NotAvailable
    }

    private fun AddressCheckResult.toIncomingResult(): IncomingAddressCheckResult {
        return when (this) {
            AddressCheckResult.NotSupported,
            AddressCheckResult.NotAllowed,
            AddressCheckResult.NotAvailable -> IncomingAddressCheckResult.Unknown

            AddressCheckResult.Clear,
            AddressCheckResult.AlphaAmlLow,
            AddressCheckResult.AlphaAmlVeryLow -> IncomingAddressCheckResult.Low

            AddressCheckResult.AlphaAmlHigh -> IncomingAddressCheckResult.Medium

            AddressCheckResult.AlphaAmlVeryHigh,
            AddressCheckResult.Detected -> IncomingAddressCheckResult.High
        }
    }
}
