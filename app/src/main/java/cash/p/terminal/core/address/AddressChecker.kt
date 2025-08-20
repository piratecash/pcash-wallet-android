package cash.p.terminal.core.address

import cash.p.terminal.core.factories.ContractValidatorFactory
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.entities.Address
import cash.p.terminal.wallet.Token

interface AddressChecker {
    suspend fun isClear(address: Address, token: Token): Boolean?
    fun supports(token: Token): Boolean
}

class ContractAddressChecker : AddressChecker {

    override suspend fun isClear(address: Address, token: Token): Boolean? {
        return checkNotNull(
            ContractValidatorFactory.get(token.blockchainType)
                ?.isContract(address.hex, token.blockchainType)
                ?.not()
        ) { "No internet connection" }
    }

    override fun supports(token: Token) =
        ContractValidatorFactory.get(token.blockchainType) != null
}

class PhishingAddressChecker(
    private val spamManager: SpamManager
) : AddressChecker {

    override suspend fun isClear(address: Address, token: Token): Boolean {
        val spamAddress = spamManager.find(address.hex.uppercase())
        return spamAddress == null
    }

    override fun supports(token: Token): Boolean {
        return EvmBlockchainManager.blockchainTypes.contains(token.blockchainType)
    }
}

class BlacklistAddressChecker(
    private val hashDitAddressValidator: HashDitAddressValidator,
    private val eip20AddressValidator: Eip20AddressValidator
) : AddressChecker {
    override suspend fun isClear(address: Address, token: Token): Boolean {
        val hashDitCheckResult = hashDitAddressValidator.isClear(address, token)
        val eip20CheckResult = eip20AddressValidator.isClear(address, token)
        return hashDitCheckResult && eip20CheckResult
    }

    override fun supports(token: Token): Boolean {
        return hashDitAddressValidator.supports(token) || eip20AddressValidator.supports(token)
    }
}

class SanctionAddressChecker(
    private val chainalysisAddressValidator: ChainalysisAddressValidator
) : AddressChecker {
    override suspend fun isClear(address: Address, token: Token): Boolean {
        return chainalysisAddressValidator.isClear(address)
    }

    override fun supports(token: Token): Boolean {
        return true
    }
}
