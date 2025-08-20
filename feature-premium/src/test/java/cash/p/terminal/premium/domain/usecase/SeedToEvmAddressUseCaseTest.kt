package cash.p.terminal.premium.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SeedToEvmAddressUseCaseTest {

    private lateinit var useCase: SeedToEvmAddressUseCase

    @Before
    fun setUp() {
        useCase = SeedToEvmAddressUseCase()
    }

    @Test
    fun `12 mnemonic seed without passphrase`() {
        val words = "elephant crew industry destroy layer consider aspect split recycle intact assist flat"
            .split(" ")
        val address = "0x7b7ebe8044d5E9452FaE1CD33304A6D2EaC6C28d".lowercase()
        
        val result = useCase(words).lowercase()
        
        assertEquals(address, result)
    }

    @Test
    fun `12 mnemonic seed with passphrase`() {
        val words = "elephant crew industry destroy layer consider aspect split recycle intact assist flat"
            .split(" ")
        val passphrase = "123"
        val address = "0xfAf339E255dFDA06907255c6FB43870d8D762476".lowercase()

        val result = useCase(words = words, passphrase = passphrase).lowercase()

        assertEquals(address, result)
    }

    @Test
    fun `24 mnemonic seed with passphrase`() {
        val words = "near expand rabbit hungry pink despair script humor expect shoot inch kick simple lion rug pottery puzzle creek giraffe wood lyrics scene trash orbit"
            .split(" ")
        val passphrase = "123"
        val address = "0x6311B808eD0093EBA889115aFf43d823591A420D".lowercase()

        val result = useCase(words = words, passphrase = passphrase).lowercase()

        assertEquals(address, result)
    }
} 