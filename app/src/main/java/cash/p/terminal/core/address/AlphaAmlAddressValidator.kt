package cash.p.terminal.core.address

import cash.p.terminal.entities.Address
import cash.p.terminal.network.alphaaml.api.AlphaAmlApi
import cash.p.terminal.network.alphaaml.data.AlphaAmlRiskGrade
import org.koin.java.KoinJavaComponent.inject

class AlphaAmlAddressValidator {
    private val alphaAmlApi: AlphaAmlApi by inject(AlphaAmlApi::class.java)

    suspend fun getRiskGrade(address: Address): AlphaAmlRiskGrade? {
        return try {
            val response = alphaAmlApi.getRiskGrade(address.hex)
            response?.let {
                when (it.status) {
                    "success" -> AlphaAmlRiskGrade.fromString(it.riskGrade)
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
