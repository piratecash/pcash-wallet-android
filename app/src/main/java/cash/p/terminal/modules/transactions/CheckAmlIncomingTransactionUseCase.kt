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
    ): IncomingAddressCheckResult {
        if (addresses.isEmpty()) {
            return IncomingAddressCheckResult.Unknown
        }

        val supportedTypes = addressCheckManager.availableCheckTypes(token)
            .filter { it in checkTypes }

        if (supportedTypes.isEmpty()) {
            return IncomingAddressCheckResult.Unknown
        }

        val results = coroutineScope {
            addresses.flatMap { address ->
                supportedTypes.map { type ->
                    async {
                        checkWithRetry(type, address, token)
                    }
                }
            }.awaitAll()
        }

        return results
            .map { it.toIncomingResult() }
            .maxByOrNull { it.ordinal }
            ?: IncomingAddressCheckResult.Unknown
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
