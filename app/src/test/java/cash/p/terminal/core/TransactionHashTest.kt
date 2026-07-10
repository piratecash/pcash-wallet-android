package cash.p.terminal.core

import junit.framework.TestCase.assertEquals
import org.junit.Test

class TransactionHashTest {

    @Test
    fun canonicalTransactionHash_hashWithPrefix_returnsLowercaseWithoutPrefix() {
        assertEquals("abcdef", "  0xAbCdEf  ".canonicalTransactionHash())
    }

    @Test
    fun canonicalTransactionHash_hashWithoutPrefix_returnsLowercase() {
        assertEquals("abcdef", "AbCdEf".canonicalTransactionHash())
    }

    @Test
    fun evmExplorerTransactionHash_hashWithoutPrefix_returnsPrefixedHash() {
        assertEquals("0xabcdef", "AbCdEf".evmExplorerTransactionHash())
    }
}
