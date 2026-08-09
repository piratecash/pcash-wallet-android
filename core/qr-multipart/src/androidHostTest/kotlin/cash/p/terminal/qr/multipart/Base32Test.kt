package cash.p.terminal.qr.multipart

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Base32Test {

    @Test
    fun encodeDecode_lengths0To64_roundTrips() {
        val random = Random(1)
        for (size in 0..64) {
            val bytes = random.nextBytes(size)
            val encoded = Base32.encode(bytes)
            assertEquals(Base32.encodedLength(size), encoded.length, "length for $size bytes")
            assertContentEquals(bytes, Base32.decode(encoded), "round trip for $size bytes")
        }
    }

    @Test
    fun decode_lengthResidue1And3And6_returnsNull() {
        for (residue in listOf(1, 3, 6)) {
            assertNull(Base32.decode("A".repeat(residue)), "residue $residue")
            assertNull(Base32.decode("A".repeat(8 + residue)), "residue ${8 + residue}")
        }
    }

    @Test
    fun decode_charOutsideAlphabet_returnsNull() {
        for (char in listOf('0', '1', '8', '9', 'a', '=', ':', ' ')) {
            assertNull(Base32.decode("AAAAAAA$char"), "char $char")
        }
    }

    @Test
    fun decode_nonZeroTrailingBits_returnsNull() {
        // Two chars carry 10 bits for a single byte: the low 2 bits must be zero.
        assertContentEquals(byteArrayOf(0), Base32.decode("AA"))
        assertNull(Base32.decode("AB"))
        // Four chars carry 20 bits for two bytes: the low 4 bits must be zero.
        assertContentEquals(byteArrayOf(0, 0), Base32.decode("AAAA"))
        assertNull(Base32.decode("AAAB"))
    }

    @Test
    fun encode_twelveBytes_returns20CharsEndingInAOrQ() {
        val random = Random(2)
        repeat(64) {
            val encoded = Base32.encode(random.nextBytes(MultipartQrFrame.MSG_ID_BYTES))
            assertEquals(MultipartQrFrame.MSG_ID_CHARS, encoded.length)
            // 12 bytes are 96 bits; the last of 20 chars carries a single bit, so only the two
            // symbols with four zero low bits can appear there.
            assertTrue(encoded.last() in listOf('A', 'Q'), "last char ${encoded.last()}")
        }
    }
}
