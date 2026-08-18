package cash.p.terminal.modules.address
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.entities.AddressUri
import cash.p.terminal.modules.balance.openSendTokenSelect
import cash.p.terminal.modules.balance.parsePrefixlessAddressUri
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test fun mainPrefixlessUris_retainCompatibleAndExactBlockchains() {
        val evm = requireNotNull(
            parsePrefixlessAddressUri(
                "0x0000000000000000000000000000000000000001?amount=1",
                EvmBlockchainManager.blockchainTypes
            )
        )
        assertEquals(EvmBlockchainManager.blockchainTypes, evm.second)
        val bsc = parsePrefixlessAddressUri(
            "0x0000000000000000000000000000000000000001?blockchain_uid=${BlockchainType.BinanceSmartChain.uid}",
            EvmBlockchainManager.blockchainTypes,
        )
        assertEquals(listOf(BlockchainType.BinanceSmartChain), requireNotNull(bsc).second)
        val bitcoinCash = parsePrefixlessAddressUri(
            "legacy?req-feature=1",
            listOf(BlockchainType.Bitcoin, BlockchainType.BitcoinCash)
        )
        assertEquals(listOf(BlockchainType.BitcoinCash), requireNotNull(bitcoinCash).second)
        val token = parsePrefixlessAddressUri("0x1?token_uid=eip20:0xToKeN", EvmBlockchainManager.blockchainTypes)
        assertEquals("eip20:0xToKeN", requireNotNull(token).first.value<String>(AddressUri.Field.TokenUid))
        assertEquals(
            listOf(TokenType.Eip20("0xtoken")),
            token.first.openSendTokenSelect(token.second, hasExplicitScheme = false).tokenTypes,
        )
        val spl = requireNotNull(
            parsePrefixlessAddressUri("recipient?token_uid=spl:mint", listOf(BlockchainType.Solana))
        )
        assertEquals(listOf(TokenType.Spl("mint")), spl.first.openSendTokenSelect(spl.second, false).tokenTypes)
        val solana = parsePrefixlessAddressUri("recipient?amount=1.5", listOf(BlockchainType.Solana))
        val monero = parsePrefixlessAddressUri("4address?tx_amount=2.5", listOf(BlockchainType.Monero))
        assertEquals(listOf(BlockchainType.Solana), requireNotNull(solana).second)
        assertEquals(listOf(BlockchainType.Monero), requireNotNull(monero).second)
        val solanaUri = requireNotNull(AddressUriParser.addressUri("solana:recipient?amount=1.5"))
        assertEquals("recipient", solanaUri.address)
        assertEquals(listOf(BlockchainType.Solana), solanaUri.openSendTokenSelect().blockchainTypes)
        assertEquals(listOf(TokenType.Native), solanaUri.openSendTokenSelect().tokenTypes)
        assertEquals(
            listOf(TokenType.Native),
            AddressUriParser.addressUri("ethereum:0x1?value=1")?.openSendTokenSelect()?.tokenTypes
        )
        assertEquals("4address", AddressUriParser.addressUri("monero:4address?tx_amount=2.5")?.address)
        val tonUri = requireNotNull(AddressUriParser.addressUri("ton://transfer/UQaddress?amount=1000000000"))
        assertEquals(listOf(TokenType.Native), tonUri.openSendTokenSelect().tokenTypes)
        assertNull(AddressUriParser.addressUri("Xaddress?callback=https://example.com//pay&amount=1"))
    }
    @Test fun process_nativeTonTransferUri_prefillsNormalizedAmountAndMemo() {
        val viewModel = createViewModel(BlockchainType.Ton)
        val processed = viewModel.process("ton://transfer/UQaddress?amount=1200000000&text=note&exp=123")
        assertEquals("UQaddress", processed)
        assertEquals(BigDecimal("1.2"), viewModel.amountUnique?.amount)
        assertEquals("note", viewModel.memoUnique?.memo)
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

    @Test fun constructor_prefilledMemo_exposesMemoEvent() {
        val viewModel = AddressParserViewModel(
            AddressUriParser(BlockchainType.Dash, TokenType.Native),
            PrefilledData("Xaddress", BigDecimal.ONE, "memo")
        )
        assertEquals(BigDecimal.ONE, viewModel.amountUnique?.amount)
        assertEquals("memo", viewModel.memoUnique?.memo)
    }
    @Test fun process_uriWithSupportedAndOptionalParameters_prefillsOnlySupportedFields() {
        val viewModel = createViewModel(BlockchainType.Dash)
        assertEquals(
            "Xaddress",
            viewModel.process("dash:Xaddress?amount=1.20&memo=note&label=label&message=message&tag=tag&IS=1&foo=bar")
        )
        assertEquals(BigDecimal("1.20"), viewModel.amountUnique?.amount)
        assertEquals("note", viewModel.memoUnique?.memo)
    }

    @Test fun process_memoOnlyUri_returnsAddressAndEmitsMemo() {
        val viewModel = createViewModel(BlockchainType.Dash)
        assertEquals("Xaddress", viewModel.process("dash:Xaddress?memo=note"))
        assertEquals("note", viewModel.memoUnique?.memo)
    }

    @Test fun acknowledgeMemo_matchingId_clearsOnlyMatchingEvent() {
        val viewModel = createViewModel(BlockchainType.Dash).also { it.process("dash:Xaddress?memo=note") }
        val event = requireNotNull(viewModel.memoUnique)
        viewModel.acknowledgeMemo(event.id + 1)
        assertEquals(event, viewModel.memoUnique)
        viewModel.acknowledgeMemo(event.id)
        assertNull(viewModel.memoUnique)
    }

    @Test fun process_sameMemoAfterClearingInput_emitsNewEvent() {
        val viewModel = createViewModel(BlockchainType.Dash).also { it.process("dash:Xaddress?memo=note") }
        val firstEvent = requireNotNull(viewModel.memoUnique)
        viewModel.acknowledgeMemo(firstEvent.id)
        viewModel.process("")
        viewModel.process("dash:Xaddress?memo=note")
        assertEquals("note", viewModel.memoUnique?.memo)
        assertNotEquals(firstEvent.id, viewModel.memoUnique?.id)
    }

    private fun createViewModel(blockchainType: BlockchainType): AddressParserViewModel {
        return AddressParserViewModel(AddressUriParser(blockchainType, TokenType.Native), null)
    }
}
