package cash.p.terminal.modules.multiswap.providers

import io.ktor.util.encodeBase64
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ton.block.AddrStd
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import java.math.BigInteger

class TonHelperKtTest {

    private val USER_WALLET_ADDRESS = "UQAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D_8noARLOaEAn"
    private val ASK_JETTON_WALLET_ADDRESS = "kQB_TOJSB7q3-Jm1O8s0jKFtqLElZDPjATs5uJGsujcjznq3"

    private val DEFAULT_DEADLINE = 15 * 60L
    private val DEFAULT_QUERY_ID = 1L

    /**
     * Serialize Cell to Base64-encoded BOC string.
     * Uses standard ton-kotlin BagOfCells serialization.
     */
    private fun bocBase64(cell: Cell): String {
        return BagOfCells(cell).toByteArray().encodeBase64()
    }

    private fun addrStd(friendly: String): AddrStd = AddrStd(friendly)

    // --- Tests for buildStonfiSwapTonToJettonPayload ---

    @Test
    fun stonfiSwap_minimumRequiredParameters_snapshotParity() {
        val payload = buildStonfiSwapTonToJettonPayloadV2(
            tonAmount = BigInteger.valueOf(100_000_000L),
            receiver = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = BigInteger.valueOf(900_000_000L),
            tokenWallet = addrStd(ASK_JETTON_WALLET_ADDRESS),
            deadline = DEFAULT_DEADLINE,
            queryId = DEFAULT_QUERY_ID
        )
        val expected = "te6ccgEBAwEA1QABZAHzg10AAAAAAAAAAUBfXhAIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAQHhZmTeKoAP6ZxKQPdW/xM2p3lmkZQttRYkrIZ8YCdnNxI1l0bkedAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzQAAAAAAAABwkACAFNDWk6QCAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAABRA="
        assertEquals(expected, bocBase64(payload))
    }

    @Test
    fun stonfiSwap_withReferralAndDifferentQueryId_snapshotParity() {
        val payload = buildStonfiSwapTonToJettonPayloadV2(
            tonAmount = BigInteger.valueOf(200_000_000L),
            receiver = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = BigInteger.valueOf(800_000_000L),
            tokenWallet = addrStd(ASK_JETTON_WALLET_ADDRESS),
            excessesAddress = addrStd(USER_WALLET_ADDRESS),
            referralAddress = addrStd(USER_WALLET_ADDRESS),
            deadline = DEFAULT_DEADLINE,
            queryId = 42L
        )
        val expected = "te6ccgEBAwEA9gABZAHzg10AAAAAAAAAKkC+vCAIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAQHhZmTeKoAP6ZxKQPdW/xM2p3lmkZQttRYkrIZ8YCdnNxI1l0bkedAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzQAAAAAAAABwkACAJVC+vCACAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAABUABCfEuqVfYHrDiNDPPM9YDx7JZlVkHPpjQP/yegBEs5og="
        assertEquals(expected, bocBase64(payload))
    }

    @Test
    fun stonfiSwap_differentDeadlineAndAmounts_snapshotParity() {
        val payload = buildStonfiSwapTonToJettonPayloadV2(
            tonAmount = BigInteger.valueOf(300_000_000L),
            receiver = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = BigInteger.valueOf(700_000_000L),
            tokenWallet = addrStd(ASK_JETTON_WALLET_ADDRESS),
            excessesAddress = addrStd(USER_WALLET_ADDRESS),
            deadline = 1234L,
            queryId = 99L
        )
        val expected = "te6ccgEBAwEA1QABZAHzg10AAAAAAAAAY0EeGjAIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAQHhZmTeKoAP6ZxKQPdW/xM2p3lmkZQttRYkrIZ8YCdnNxI1l0bkedAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzQAAAAAAAACaUACAFNCm5JwCAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAABRA="
        assertEquals(expected, bocBase64(payload))
    }

    // --- Tests for buildJettonToTonPayload ---

    @Test
    fun jettonToTon_minimumRequiredParameters_snapshotParity() {
        val payload = buildJettonToTonPayloadV2(
            amount = BigInteger.valueOf(500_000_000L),
            router = addrStd(USER_WALLET_ADDRESS),
            ptonWallet = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = BigInteger.valueOf(100_000_000L),
            forwardGas = BigInteger.valueOf(2000L),
            queryId = DEFAULT_QUERY_ID,
            deadline = DEFAULT_DEADLINE,
            referralAddress = null
        )
        val expected = "te6ccgEBAwEA+QABrA+KfqUAAAAAAAAAAUHc1lAIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaBA+hAQHhZmTeKoACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzQAAAAAAAABwkACAFNAX14QCAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAABRA="
        assertEquals(expected, bocBase64(payload))
    }

    @Test
    fun jettonToTon_withReferralAndCustomFee_snapshotParity() {
        val payload = buildJettonToTonPayloadV2(
            amount = BigInteger.valueOf(600_000_000L),
            router = addrStd(USER_WALLET_ADDRESS),
            ptonWallet = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            excessesAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = BigInteger.valueOf(200_000_000L),
            forwardGas = BigInteger.valueOf(3000L),
            queryId = 77L,
            refFee = 50,
            deadline = DEFAULT_DEADLINE,
            referralAddress = addrStd(USER_WALLET_ADDRESS)
        )
        val expected = "te6ccgECAwEAARoAAawPin6lAAAAAAAAAE1CPDRgCAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0QAEJ8S6pV9gesOI0M88z1gPHslmVWQc+mNA//J6AESzmgQXcQEB4WZk3iqAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0QAEJ8S6pV9gesOI0M88z1gPHslmVWQc+mNA//J6AESzmiAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAAAAAAAAcJAAgCVQL68IAgAIT4l1Sr7A9YcRoZ55nrAePZLMqsg59MaB/+T0AIlnNAAABlAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaI"
        assertEquals(expected, bocBase64(payload))
    }

    @Test
    fun jettonToTon_differentAmountsAndQueryId_snapshotParity() {
        val payload = buildJettonToTonPayloadV2(
            amount = BigInteger.valueOf(700_000_000L),
            router = addrStd(USER_WALLET_ADDRESS),
            ptonWallet = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = BigInteger.valueOf(50_000_000L),
            forwardGas = BigInteger.valueOf(5000L),
            queryId = 123L,
            deadline = DEFAULT_DEADLINE,
            referralAddress = null
        )
        val expected = "te6ccgEBAwEA+QABrA+KfqUAAAAAAAAAe0KbknAIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaBCcRAQHhZmTeKoACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzQAAAAAAAABwkACAFNAL68ICAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAABRA="
        assertEquals(expected, bocBase64(payload))
    }
}
