package cash.p.terminal.modules.send.tron

import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.entities.Address
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tronkit.models.Address as TronAddress
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendTronAddressServiceTest {

    private val adapter = mockk<ISendTronAdapter>(relaxed = true)
    private val service = SendTronAddressService(adapter, trxToken)
    private val address = Address(TRON_ADDRESS)
    private val tronAddress = TronAddress.fromBase58(TRON_ADDRESS)

    @Before
    fun setUp() {
        every { adapter.isOwnAddress(any()) } returns false
    }

    @Test
    fun setAddress_validAddress_allowsSendWithoutNetworkValidation() {
        service.setAddress(address)

        val state = service.stateFlow.value

        assertEquals(address, state.address)
        assertEquals(tronAddress, state.tronAddress)
        assertNull(state.addressError)
        assertTrue(state.canBeSend)
        verify(exactly = 1) { adapter.isOwnAddress(tronAddress) }
    }

    @Test
    fun setAddress_corruptedChecksumAddress_returnsValidationErrorWithoutCheckingOwnership() {
        service.setAddress(Address(TRON_ADDRESS.dropLast(1) + "s"))

        val state = service.stateFlow.value

        assertNull(state.tronAddress)
        assertNotNull(state.addressError)
        assertFalse(state.canBeSend)
        verify(exactly = 0) { adapter.isOwnAddress(any()) }
    }

    @Test
    fun setAddress_ownNativeAddress_returnsSelfSendError() {
        every { adapter.isOwnAddress(tronAddress) } returns true

        service.setAddress(address)

        val state = service.stateFlow.value

        assertEquals(tronAddress, state.tronAddress)
        assertNotNull(state.addressError)
        assertFalse(state.canBeSend)
    }

    private companion object {
        const val TRON_ADDRESS = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"

        val trxToken = Token(
            coin = Coin(uid = "tron", name = "TRON", code = "TRX"),
            blockchain = Blockchain(BlockchainType.Tron, "TRON", null),
            type = TokenType.Native,
            decimals = 6,
        )
    }
}
