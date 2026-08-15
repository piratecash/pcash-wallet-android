package cash.p.terminal.modules.sendtokenselect
import cash.p.terminal.entities.AddressUri
import cash.p.terminal.wallet.Wallet
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
class SendTokenSelectFragmentTest {
    @Test fun toSendInput_uriWithOptionalParameters_preservesOnlySupportedPrefill() {
        val uri = AddressUri("dash").apply {
            address = "Xaddress"
            parameters = mutableMapOf(
                AddressUri.Field.Amount to "1.20",
                AddressUri.Field.Memo to "memo",
            )
        }
        val input = SendTokenSelectFragment.Input(null, null, PrefilledData.from(uri))
        val sendInput = input.toSendInput(mockk<Wallet>(), "Dash"); assertEquals("Xaddress", sendInput.prefilledData?.address)
        assertEquals("1.20", sendInput.prefilledData?.amount.toString()); assertEquals("memo", sendInput.prefilledData?.memo)
    }
}
