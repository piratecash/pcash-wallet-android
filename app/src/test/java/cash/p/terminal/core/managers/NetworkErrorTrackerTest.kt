package cash.p.terminal.core.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkErrorTrackerTest {

    @Test
    fun boundedStackTraceToString_deepStack_capsFramesAndShrinks() {
        val deep = deepException(40)

        val bounded = deep.boundedStackTraceToString()

        val frameLines = bounded.lines().count { it.startsWith("\tat ") }
        assertEquals(MAX_TRACE_FRAMES_PER_CAUSE, frameLines)
        assertTrue("dropped-frame marker expected", bounded.contains("more"))
        assertTrue(
            "bounded trace must be smaller than the full one",
            bounded.length < deep.stackTraceToString().length
        )
    }

    @Test
    fun boundedStackTraceToString_deepCauseChain_capsDepth() {
        val chain = RuntimeException(
            "l0",
            RuntimeException(
                "l1",
                RuntimeException(
                    "l2",
                    RuntimeException("l3", RuntimeException("l4"))
                )
            )
        )

        val bounded = chain.boundedStackTraceToString()

        // Primary header (l0) + at most MAX_TRACE_CAUSE_DEPTH-1 "Caused by" levels (l1, l2).
        val causedByLines = bounded.lines().count { it.startsWith("Caused by:") }
        assertEquals(MAX_TRACE_CAUSE_DEPTH - 1, causedByLines)
        assertTrue("cause-truncation marker expected", bounded.contains("truncated"))
        assertFalse("levels beyond the cap must be dropped", bounded.contains("l3"))
    }

    private fun deepException(depth: Int): RuntimeException =
        try {
            recurse(depth)
        } catch (e: RuntimeException) {
            e
        }

    private fun recurse(depth: Int): Nothing {
        if (depth <= 0) error("boom")
        recurse(depth - 1)
    }
}
