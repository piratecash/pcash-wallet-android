package cash.p.terminal.core.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class FlipGestureRecognizerTest {

    @Test
    fun onSensorChanged_faceUpOnly_doesNotEmit() {
        val recognizer = FlipGestureRecognizer()

        val emissions = feed(recognizer, FACE_UP_Z, startMs = 0L)

        assertEquals(0, emissions)
    }

    @Test
    fun onSensorChanged_flipWithinWindow_emitsOnce() {
        val recognizer = initializedRecognizer()

        val emissions = feed(recognizer, FACE_DOWN_Z, startMs = 300L) +
            feed(recognizer, FACE_UP_Z, startMs = 900L)

        assertEquals(1, emissions)
    }

    @Test
    fun onSensorChanged_flipAfterWindow_doesNotEmit() {
        val recognizer = initializedRecognizer()

        val emissions = feed(recognizer, FACE_DOWN_Z, startMs = 300L) +
            feed(recognizer, FACE_UP_Z, startMs = 4_000L)

        assertEquals(0, emissions)
    }

    @Test
    fun onSensorChanged_consecutiveFlips_emitsForEach() {
        val recognizer = initializedRecognizer()

        val emissions = feed(recognizer, FACE_DOWN_Z, startMs = 300L) +
            feed(recognizer, FACE_UP_Z, startMs = 900L) +
            feed(recognizer, FACE_DOWN_Z, startMs = 1_500L) +
            feed(recognizer, FACE_UP_Z, startMs = 2_100L)

        assertEquals(2, emissions)
    }

    @Test
    fun onSensorChanged_nonFaceDownSamples_doesNotEmit() {
        val recognizer = initializedRecognizer()

        val emissions = feed(recognizer, -7.5f, startMs = 300L, sampleCount = 10) +
            feed(recognizer, FACE_UP_Z, startMs = 900L)

        assertEquals(0, emissions)
    }

    @Test
    fun onSensorChanged_oppositeAccelerationImpulses_doesNotEmit() {
        val recognizer = initializedRecognizer()

        val emissions = feed(recognizer, -IMPULSE_Z, startMs = 300L, sampleCount = 1) +
            feed(recognizer, IMPULSE_Z, startMs = 350L, sampleCount = 1)

        assertEquals(0, emissions)
    }

    @Test
    fun reset_partialGesture_discardsIt() {
        val recognizer = initializedRecognizer()
        feed(recognizer, FACE_DOWN_Z, startMs = 300L)

        recognizer.reset()
        val emissions = feed(recognizer, FACE_UP_Z, startMs = 900L)

        assertEquals(0, emissions)
    }

    private fun initializedRecognizer() = FlipGestureRecognizer().also {
        feed(it, FACE_UP_Z, startMs = 0L)
    }

    private fun feed(
        recognizer: FlipGestureRecognizer,
        z: Float,
        startMs: Long,
        sampleCount: Int = SAMPLES_PER_ORIENTATION,
    ): Int = (0 until sampleCount).count { index ->
        recognizer.onSensorChanged(z, startMs + index * SAMPLE_INTERVAL_MS)
    }

    private companion object {
        const val FACE_UP_Z = 9.81f
        const val FACE_DOWN_Z = -9.81f
        const val IMPULSE_Z = 30f
        const val SAMPLE_INTERVAL_MS = 50L
        const val SAMPLES_PER_ORIENTATION = 5
    }
}
