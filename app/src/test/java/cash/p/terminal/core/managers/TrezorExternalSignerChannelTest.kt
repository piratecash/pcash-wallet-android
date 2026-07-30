package cash.p.terminal.core.managers

import cash.p.terminal.trezorkit.TrezorCancelledException
import cash.p.terminal.trezorkit.TrezorTransportException
import cash.p.terminal.trezorkit.TrezorUsbStaleChannelException
import cash.p.terminal.trezorkit.transport.TrezorRawUsbChannel
import com.piratecash.monero.signer.ExternalSignerError
import com.piratecash.monero.signer.ExternalSignerException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class TrezorExternalSignerChannelTest {
    @Test
    fun readPacket_channelReturnsPacket_forwardsDeadlineAndPacket() {
        val packet = ByteArray(PACKET_SIZE) { it.toByte() }
        var actualDeadline = 0L
        val channel = TrezorExternalSignerChannel(
            rawChannel(
                read = {
                    actualDeadline = it
                    packet
                },
            ),
        )

        val result = channel.readPacket(DEADLINE_NANOS)

        assertEquals(DEADLINE_NANOS, actualDeadline)
        assertArrayEquals(packet, result)
    }

    @Test
    fun readPacket_staleTrezorChannel_mapsStaleSignerError() {
        val cause = TrezorUsbStaleChannelException()
        val channel = TrezorExternalSignerChannel(
            rawChannel(read = { throw cause }),
        )

        val error = assertSignerError {
            channel.readPacket(DEADLINE_NANOS)
        }

        assertEquals(ExternalSignerError.STALE_CHANNEL, error.error)
        assertSame(cause, error.cause)
    }

    @Test
    fun cancel_trezorCancellation_mapsCancelledSignerError() {
        val cause = TrezorCancelledException("cancelled")
        val channel = TrezorExternalSignerChannel(
            rawChannel(cancel = { throw cause }),
        )

        val error = assertSignerError(channel::cancel)

        assertEquals(ExternalSignerError.CANCELLED, error.error)
        assertSame(cause, error.cause)
    }

    @Test
    fun writePacket_transportFailure_mapsChannelFailure() {
        val cause = TrezorTransportException("failed")
        val channel = TrezorExternalSignerChannel(
            rawChannel(write = { throw cause }),
        )

        val error = assertSignerError {
            channel.writePacket(ByteArray(PACKET_SIZE))
        }

        assertEquals(ExternalSignerError.CHANNEL_FAILURE, error.error)
        assertSame(cause, error.cause)
    }

    @Test
    fun writePacket_jvmError_propagatesWithoutMapping() {
        val cause = LinkageError("broken runtime")
        val channel = TrezorExternalSignerChannel(
            rawChannel(write = { throw cause }),
        )

        val actual = try {
            channel.writePacket(ByteArray(PACKET_SIZE))
            fail("Expected LinkageError")
            throw AssertionError("unreachable")
        } catch (error: LinkageError) {
            error
        }

        assertSame(cause, actual)
    }

    private fun rawChannel(
        write: (ByteArray) -> Unit = {},
        read: (Long) -> ByteArray = { ByteArray(PACKET_SIZE) },
        cancel: () -> Unit = {},
    ) = object : TrezorRawUsbChannel {
        override fun writePacket(packet: ByteArray) = write(packet)

        override fun readPacket(): ByteArray = read(Long.MAX_VALUE)

        override fun readPacket(deadlineNanos: Long): ByteArray = read(deadlineNanos)

        override fun cancel() = cancel()
    }

    private fun assertSignerError(block: () -> Unit): ExternalSignerException =
        try {
            block()
            fail("Expected ExternalSignerException")
            throw AssertionError("unreachable")
        } catch (error: ExternalSignerException) {
            error
        }

    private companion object {
        const val PACKET_SIZE = 64
        const val DEADLINE_NANOS = 123L
    }
}
