package cash.p.terminal.core.adapters

import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.core.managers.TronKitWrapper
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.transaction.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

abstract class BaseTronAdapter(
    tronKitWrapper: TronKitWrapper,
    val decimal: Int
) : IAdapter, IBalanceAdapter, IReceiveAdapter {

    val tronKit = tronKitWrapper.tronKit
    protected val signer: Signer? = tronKitWrapper.signer

    override val debugInfo: String
        get() = ""

    override val statusInfo: Map<String, Any>
        get() = tronKit.statusInfo()

    // IReceiveAdapter

    override suspend fun isAddressActive(address: String): Boolean {
        val tronAddress = Address.fromBase58(address)
        return tronKit.isAccountActive(tronAddress)
    }

    override val receiveAddress: String
        get() = tronKit.address.base58

    override val isMainNet: Boolean
        get() = tronKit.network == Network.Mainnet

    // ISendTronAdapter

    suspend fun isAddressActive(address: Address): Boolean = withContext(Dispatchers.IO) {
        tronKit.isAccountActive(address)
    }

    fun isOwnAddress(address: Address): Boolean {
        return address == tronKit.address
    }

    protected fun balanceInBigDecimal(balance: BigInteger?, decimal: Int): BigDecimal {
        balance?.toBigDecimal()?.let {
            return scaleDown(it, decimal)
        } ?: return BigDecimal.ZERO
    }

    protected fun scaleDown(amount: BigDecimal, decimals: Int = decimal): BigDecimal {
        return amount.movePointLeft(decimals).stripTrailingZeros()
    }

    protected fun scaleUp(amount: BigDecimal, decimals: Int = decimal): BigInteger {
        return amount.movePointRight(decimals).toBigInteger()
    }

    companion object {
        const val confirmationsThreshold: Int = 19
    }

}
