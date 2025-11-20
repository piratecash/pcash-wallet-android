package cash.p.terminal.core.address

import cash.p.terminal.core.factories.ContractValidatorFactory
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.send.address.AddressCheckResult
import cash.p.terminal.network.alphaaml.data.AlphaAmlRiskGrade
import cash.p.terminal.wallet.Token

interface AddressChecker {
    suspend fun isClear(address: Address, token: Token): AddressCheckResult
    fun supports(token: Token): Boolean
}

class ContractAddressChecker : AddressChecker {

    override suspend fun isClear(address: Address, token: Token): AddressCheckResult {
        return if (checkNotNull(
                ContractValidatorFactory.get(token.blockchainType)
                    ?.isContract(address.hex, token.blockchainType)
                    ?.not()
            ) { "No internet connection" }
        ) AddressCheckResult.Clear else AddressCheckResult.Detected
    }

    override fun supports(token: Token) =
        ContractValidatorFactory.get(token.blockchainType) != null
}

class PhishingAddressChecker(
    private val spamManager: SpamManager
) : AddressChecker {

    override suspend fun isClear(address: Address, token: Token): AddressCheckResult {
        val spamAddress = spamManager.find(address.hex.uppercase())
        return if (spamAddress == null) AddressCheckResult.Clear else AddressCheckResult.Detected
    }

    override fun supports(token: Token): Boolean {
        return EvmBlockchainManager.blockchainTypes.contains(token.blockchainType)
    }
}

class BlacklistAddressChecker(
    private val hashDitAddressValidator: HashDitAddressValidator,
    private val eip20AddressValidator: Eip20AddressValidator
) : AddressChecker {
    override suspend fun isClear(address: Address, token: Token): AddressCheckResult {
        val hashDitCheckResult = hashDitAddressValidator.isClear(address, token)
        val eip20CheckResult = eip20AddressValidator.isClear(address, token)
        return if (hashDitCheckResult && eip20CheckResult) {
            AddressCheckResult.Clear
        } else {
            AddressCheckResult.Detected
        }
    }

    override fun supports(token: Token): Boolean {
        return hashDitAddressValidator.supports(token) || eip20AddressValidator.supports(token)
    }
}

class AmlAddressChecker(
    private val alphaAmlAddressValidator: AlphaAmlAddressValidator
) : AddressChecker {
    override suspend fun isClear(address: Address, token: Token): AddressCheckResult {
        return when (alphaAmlAddressValidator.getRiskGrade(address)) {
            null -> AddressCheckResult.NotAvailable
            AlphaAmlRiskGrade.VeryLow -> AddressCheckResult.AlphaAmlVeryLow
            AlphaAmlRiskGrade.Low -> AddressCheckResult.AlphaAmlLow
            AlphaAmlRiskGrade.High -> AddressCheckResult.AlphaAmlHigh
            AlphaAmlRiskGrade.VeryHigh -> AddressCheckResult.AlphaAmlVeryHigh
        }
    }

    override fun supports(token: Token): Boolean {
        return EvmBlockchainManager.blockchainTypes.contains(token.blockchainType)
    }
}

class SanctionAddressChecker(
    private val chainalysisAddressValidator: ChainalysisAddressValidator
) : AddressChecker {
    override suspend fun isClear(address: Address, token: Token): AddressCheckResult {
        return if (chainalysisAddressValidator.isClear(address)) {
            AddressCheckResult.Clear
        } else {
            AddressCheckResult.Detected
        }
    }

    override fun supports(token: Token): Boolean {
        return true
    }
}
