package cash.p.terminal.modules.address
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
class AddressParserViewModelTest {
    @Test
    fun process_legacyAmountUri_returnsAddressAndEmitsAmount() {
        val viewModel = createViewModel(BlockchainType.Bitcoin)
        assertEquals("1address", viewModel.process("bitcoin:1address?amount=1.25"))
        assertEquals(BigDecimal("1.25"), viewModel.amountUnique?.amount)
    }

    @Test
    fun process_evmValueUri_returnsAddressAndEmitsAmount() {
        val viewModel = createViewModel(BlockchainType.Ethereum)
        assertEquals("0xaddress", viewModel.process("ethereum:0xaddress?value=2"))
        assertEquals(BigDecimal("2"), viewModel.amountUnique?.amount)
    }

    @Test
    fun process_leadingAmpersandAmountUri_returnsAddressAndEmitsAmount() {
        val viewModel = createViewModel(BlockchainType.Dash)
        assertEquals("Xaddress", viewModel.process("dash:Xaddress&amount=3"))
        assertEquals(BigDecimal("3"), viewModel.amountUnique?.amount)
    }

    @Test
    fun process_moneroTxAmountUri_returnsAddressAndEmitsAmount() {
        val viewModel = createViewModel(BlockchainType.Monero)
        assertEquals("4address", viewModel.process("monero:4address?tx_amount=2.5"))
        assertEquals(BigDecimal("2.5"), viewModel.amountUnique?.amount)
    }

    @Test
    fun process_prefixlessSolanaAndMoneroUris_returnsAddressesAndEmitsAmounts() {
        val solana = createViewModel(BlockchainType.Solana)
        val monero = createViewModel(BlockchainType.Monero)
        assertEquals("recipient", solana.process("recipient?amount=1.5"))
        assertEquals(BigDecimal("1.5"), solana.amountUnique?.amount)
        assertEquals("4address", monero.process("4address?tx_amount=2.5"))
        assertEquals(BigDecimal("2.5"), monero.amountUnique?.amount)
    }

    @Test
    fun process_amountAndValueUri_prefersAmount() {
        val viewModel = createViewModel(BlockchainType.Ethereum)
        assertEquals("0xaddress", viewModel.process("ethereum:0xaddress?value=2&amount=1"))
        assertEquals(BigDecimal.ONE, viewModel.amountUnique?.amount)
    }

    @Test
    fun process_optionalParametersWithoutAmount_doesNotEmitAmount() {
        val viewModel = createViewModel(BlockchainType.Dash)
        assertEquals("dash:Xaddress&IS=1", viewModel.process("dash:Xaddress&IS=1"))
        assertNull(viewModel.amountUnique)
    }

    @Test
    fun process_afterClearingInput_processesNextEntry() {
        val viewModel = createViewModel(BlockchainType.Bitcoin)

        viewModel.process("bitcoin:1address?amount=1")
        viewModel.process("")
        assertEquals("1next", viewModel.process("bitcoin:1next?amount=2"))
        assertEquals(BigDecimal("2"), viewModel.amountUnique?.amount)
    }

    private fun createViewModel(blockchainType: BlockchainType): AddressParserViewModel {
        return AddressParserViewModel(AddressUriParser(blockchainType, TokenType.Native), null)
    }
}
