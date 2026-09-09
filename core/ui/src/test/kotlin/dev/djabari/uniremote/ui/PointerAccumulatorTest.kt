package dev.djabari.uniremote.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PointerAccumulatorTest {

    private val accumulator = PointerAccumulator()

    @Test
    fun `sub-unit deltas accumulate until integer threshold is reached`() {
        accumulator.reset()

        // 0.2f * 1.0 = 0.2f -> returns null
        val first = accumulator.add(0.2f, 0.2f, sensitivity = 1.0f)
        assertThat(first).isNull()

        // 0.4f * 1.0 = 0.4f (total 0.6f) -> returns null
        val second = accumulator.add(0.4f, 0.4f, sensitivity = 1.0f)
        assertThat(second).isNull()

        // 0.5f * 1.0 = 0.5f (total 1.1f) -> returns PointerDelta(1, 1)
        val third = accumulator.add(0.5f, 0.5f, sensitivity = 1.0f)
        assertThat(third).isNotNull()
        assertThat(third!!.dx).isEqualTo(1)
        assertThat(third.dy).isEqualTo(1)
    }

    @Test
    fun `reset clears remainder`() {
        accumulator.reset()
        accumulator.add(0.4f, 0.4f, 1.0f)
        accumulator.reset()

        val afterReset = accumulator.add(0.4f, 0.4f, 1.0f)
        assertThat(afterReset).isNull()
    }
}
