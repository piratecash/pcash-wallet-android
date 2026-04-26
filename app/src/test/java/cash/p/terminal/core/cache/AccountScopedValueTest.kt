package cash.p.terminal.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountScopedValueTest {

    @Test
    fun read_initial_returnsNull() {
        var stored: String? by AccountScopedValue<String> { "acc-1" }

        assertNull(stored)
    }

    @Test
    fun read_afterWrite_returnsValue() {
        var stored: String? by AccountScopedValue<String> { "acc-1" }

        stored = "hello"

        assertEquals("hello", stored)
    }

    @Test
    fun read_accountChanged_dropsValue() {
        var account = "acc-1"
        var stored: String? by AccountScopedValue<String> { account }
        stored = "hello"
        account = "acc-2"

        assertNull(stored)
    }

    @Test
    fun read_accountReturnedAfterRead_doesNotResurrectValue() {
        var account = "acc-1"
        var stored: String? by AccountScopedValue<String> { account }
        stored = "hello"
        account = "acc-2"
        assertNull(stored)
        account = "acc-1"

        assertNull(stored)
    }

    @Test
    fun read_accountSwitchedAndReturnedWithoutAccessBetween_keepsValue() {
        var account = "acc-1"
        var stored: String? by AccountScopedValue<String> { account }
        stored = "hello"
        account = "acc-2"
        // intentionally no read while on acc-2
        account = "acc-1"

        assertEquals("hello", stored)
    }

    @Test
    fun write_afterAccountChange_storesAgainstNewAccount() {
        var account = "acc-1"
        var stored: String? by AccountScopedValue<String> { account }
        stored = "first"
        account = "acc-2"
        stored = "second"

        assertEquals("second", stored)
    }

    @Test
    fun read_nullAccountThroughout_keepsValue() {
        var stored: String? by AccountScopedValue<String> { null }
        stored = "hello"

        assertEquals("hello", stored)
    }

    @Test
    fun read_accountChangedToNull_dropsValue() {
        var account: String? = "acc-1"
        var stored: String? by AccountScopedValue<String> { account }
        stored = "hello"
        account = null

        assertNull(stored)
    }

    @Test
    fun write_null_clearsValue() {
        var stored: String? by AccountScopedValue<String> { "acc-1" }
        stored = "hello"
        stored = null

        assertNull(stored)
    }
}
