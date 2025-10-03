package cash.p.terminal.modules.multiswap.providers

import io.ktor.util.encodeBase64
import io.ktor.utils.io.core.Output
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.writeFully
import io.ktor.utils.io.core.writeInt
import io.ktor.utils.io.core.writeIntLittleEndian
import io.ktor.utils.io.core.writeShort
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ton.bigint.toBigInt
import org.ton.block.AddrStd
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import java.util.zip.CRC32C
import kotlin.experimental.and

class TonHelperKtTest {

    private val USER_WALLET_ADDRESS = "UQAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D_8noARLOaEAn"
    private val ASK_JETTON_WALLET_ADDRESS = "kQB_TOJSB7q3-Jm1O8s0jKFtqLElZDPjATs5uJGsujcjznq3"

    private val DEFAULT_DEADLINE = 15 * 60L
    private val DEFAULT_QUERY_ID = 1L

    private fun bocBase64(cell: Cell): String =
        buildPacket {
            writeBagOfCells(BagOfCells(cell), hasIndex = false, hasCrc32c = true)
        }.readBytes().encodeBase64()

    private fun Output.writeBagOfCells(
        bagOfCells: BagOfCells,
        hasIndex: Boolean = false,
        hasCrc32c: Boolean = false,
        hasCacheBits: Boolean = false,
        flags: Int = 0
    ) {
        val serializedBagOfCells =
            serializeBagOfCells(bagOfCells, hasIndex, hasCrc32c, hasCacheBits, flags)
        if (hasCrc32c) {
            val c = CRC32C()
            c.update(serializedBagOfCells, 0, serializedBagOfCells.size)
            val crc = c.value.toInt()
            writeFully(serializedBagOfCells)
            writeIntLittleEndian(crc)
        } else {
            writeFully(serializedBagOfCells)
        }
    }

    private fun serializeBagOfCells(
        bagOfCells: BagOfCells,
        hasIndex: Boolean,
        hasCrc32c: Boolean,
        hasCacheBits: Boolean,
        flags: Int
    ): ByteArray = buildPacket {
        val cells = bagOfCells.toList()
        val cellsCount = cells.size
        val rootsCount = bagOfCells.roots.size
        var sizeBytes = 0
        while (cellsCount >= (1L shl (sizeBytes shl 3))) {
            sizeBytes++
        }

        val serializedCells = cells.mapIndexed { _, cell: Cell ->
            buildPacket {
                val d1 = cell.getRefsDescriptor()
                writeByte(d1)
                val d2 = cell.getBitsDescriptor()
                writeByte(d2)
                val cellData = cell.bits.toByteArray(
                    augment = (d2 and 1) != 0.toByte()
                )
                writeFully(cellData)
                cell.refs.forEach { reference ->
                    val refIndex = cells.indexOf(reference)
                    writeInt(refIndex, sizeBytes)
                }
            }
        }

        var fullSize = 0
        val sizeIndex = ArrayList<Int>()
        serializedCells.forEach { serializedCell ->
            sizeIndex.add(fullSize)
            fullSize += serializedCell.remaining.toInt()
        }
        var offsetBytes = 0
        while (fullSize >= (1L shl (offsetBytes shl 3))) {
            offsetBytes++
        }

        writeInt(BagOfCells.BOC_GENERIC_MAGIC)

        var flagsByte = 0
        if (hasIndex) {
            flagsByte = flagsByte or (1 shl 7)
        }
        if (hasCrc32c) {
            flagsByte = flagsByte or (1 shl 6)
        }
        if (hasCacheBits) {
            flagsByte = flagsByte or (1 shl 5)
        }
        flagsByte = flagsByte or flags
        flagsByte = flagsByte or sizeBytes
        writeByte(flagsByte.toByte())

        writeByte(offsetBytes.toByte())
        writeInt(cellsCount, sizeBytes)
        writeInt(rootsCount, sizeBytes)
        writeInt(0, sizeBytes)
        writeInt(fullSize, offsetBytes)
        bagOfCells.roots.forEach { root ->
            val rootIndex = cells.indexOf(root)
            writeInt(rootIndex, sizeBytes)
        }
        if (hasIndex) {
            serializedCells.forEachIndexed { index, _ ->
                writeInt(sizeIndex[index], offsetBytes)
            }
        }
        serializedCells.forEach { serializedCell ->
            val bytes = serializedCell.readBytes()
            writeFully(bytes)
        }
    }.readBytes()

    private fun Output.writeInt(value: Int, bytes: Int) {
        when (bytes) {
            1 -> writeByte(value.toByte())
            2 -> writeShort(value.toShort())
            3 -> {
                writeByte((value shr Short.SIZE_BITS).toByte())
                writeShort(value.toShort())
            }
            else -> writeInt(value)
        }
    }

    private fun addrStd(friendly: String) =
        AddrStd(friendly)

    // --- Tests for buildStonfiSwapTonToJettonPayload ---

    @Test
    fun stonfiSwap_minimumRequiredParameters_snapshotParity() {
        val payload = buildStonfiSwapTonToJettonPayloadV2(
            tonAmount = 100_000_000L.toBigInt(),
            receiver = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = 900_000_000L.toBigInt(),
            tokenWallet = addrStd(ASK_JETTON_WALLET_ADDRESS),
            deadline = DEFAULT_DEADLINE,
            queryId = DEFAULT_QUERY_ID
        )
        val expected = "te6cckEBAwEA1QABZAHzg10AAAAAAAAAAUBfXhAIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAQHhZmTeKoAP6ZxKQPdW/xM2p3lmkZQttRYkrIZ8YCdnNxI1l0bkedAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzQAAAAAAAABwkACAFNDWk6QCAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAABRCVI6ko"
        assertEquals(expected, bocBase64(payload))
    }

    @Test
    fun stonfiSwap_withReferralAndDifferentQueryId_snapshotParity() {
        val payload = buildStonfiSwapTonToJettonPayloadV2(
            tonAmount = 200_000_000L.toBigInt(),
            receiver = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = 800_000_000L.toBigInt(),
            tokenWallet = addrStd(ASK_JETTON_WALLET_ADDRESS),
            excessesAddress = addrStd(USER_WALLET_ADDRESS),
            referralAddress = addrStd(USER_WALLET_ADDRESS),
            deadline = DEFAULT_DEADLINE,
            queryId = 42L
        )
        val expected = "te6cckEBAwEA9gABZAHzg10AAAAAAAAAKkC+vCAIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAQHhZmTeKoAP6ZxKQPdW/xM2p3lmkZQttRYkrIZ8YCdnNxI1l0bkedAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzQAAAAAAAABwkACAJVC+vCACAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAABUABCfEuqVfYHrDiNDPPM9YDx7JZlVkHPpjQP/yegBEs5ogEkJnP"
        assertEquals(expected, bocBase64(payload))
    }

    @Test
    fun stonfiSwap_differentDeadlineAndAmounts_snapshotParity() {
        val payload = buildStonfiSwapTonToJettonPayloadV2(
            tonAmount = 300_000_000L.toBigInt(),
            receiver = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = 700_000_000L.toBigInt(),
            tokenWallet = addrStd(ASK_JETTON_WALLET_ADDRESS),
            excessesAddress = addrStd(USER_WALLET_ADDRESS),
            deadline = 1234L,
            queryId = 99L
        )
        val expected = "te6cckEBAwEA1QABZAHzg10AAAAAAAAAY0EeGjAIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAQHhZmTeKoAP6ZxKQPdW/xM2p3lmkZQttRYkrIZ8YCdnNxI1l0bkedAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzQAAAAAAAACaUACAFNCm5JwCAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAABRApdol9"
        assertEquals(expected, bocBase64(payload))
    }

    // --- Tests for buildJettonToTonPayload ---

    @Test
    fun jettonToTon_minimumRequiredParameters_snapshotParity() {
        val payload = buildJettonToTonPayloadV2(
            amount = 500_000_000L.toBigInt(),
            router = addrStd(USER_WALLET_ADDRESS),
            ptonWallet = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = 100_000_000L.toBigInt(),
            forwardGas = 2000L.toBigInt(),
            queryId = DEFAULT_QUERY_ID,
            deadline = DEFAULT_DEADLINE,
            referralAddress = null
        )
        val expected = "te6cckEBAwEA+QABrA+KfqUAAAAAAAAAAUHc1lAIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaBA+hAQHhZmTeKoACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzQAAAAAAAABwkACAFNAX14QCAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAABRBxiaXI"
        assertEquals(expected, bocBase64(payload))
    }

    @Test
    fun jettonToTon_withReferralAndCustomFee_snapshotParity() {
        val payload = buildJettonToTonPayloadV2(
            amount = 600_000_000L.toBigInt(),
            router = addrStd(USER_WALLET_ADDRESS),
            ptonWallet = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            excessesAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = 200_000_000L.toBigInt(),
            forwardGas = 3000L.toBigInt(),
            queryId = 77L,
            refFee = 50,
            deadline = DEFAULT_DEADLINE,
            referralAddress = addrStd(USER_WALLET_ADDRESS)
        )
        val expected = "te6cckECAwEAARoAAawPin6lAAAAAAAAAE1CPDRgCAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0QAEJ8S6pV9gesOI0M88z1gPHslmVWQc+mNA//J6AESzmgQXcQEB4WZk3iqAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0QAEJ8S6pV9gesOI0M88z1gPHslmVWQc+mNA//J6AESzmiAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAAAAAAAAcJAAgCVQL68IAgAIT4l1Sr7A9YcRoZ55nrAePZLMqsg59MaB/+T0AIlnNAAABlAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIq3tsUA=="
        assertEquals(expected, bocBase64(payload))
    }

    @Test
    fun jettonToTon_differentAmountsAndQueryId_snapshotParity() {
        val payload = buildJettonToTonPayloadV2(
            amount = 700_000_000L.toBigInt(),
            router = addrStd(USER_WALLET_ADDRESS),
            ptonWallet = addrStd(USER_WALLET_ADDRESS),
            refundAddress = addrStd(USER_WALLET_ADDRESS),
            minOut = 50_000_000L.toBigInt(),
            forwardGas = 5000L.toBigInt(),
            queryId = 123L,
            deadline = DEFAULT_DEADLINE,
            referralAddress = null
        )
        val expected = "te6cckEBAwEA+QABrA+KfqUAAAAAAAAAe0KbknAIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaBCcRAQHhZmTeKoACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzRAAQnxLqlX2B6w4jQzzzPWA8eyWZVZBz6Y0D/8noARLOaIACE+JdUq+wPWHEaGeeZ6wHj2SzKrIOfTGgf/k9ACJZzQAAAAAAAABwkACAFNAL68ICAAhPiXVKvsD1hxGhnnmesB49ksyqyDn0xoH/5PQAiWc0AAABRDTUFzF"
        assertEquals(expected, bocBase64(payload))
    }
}
