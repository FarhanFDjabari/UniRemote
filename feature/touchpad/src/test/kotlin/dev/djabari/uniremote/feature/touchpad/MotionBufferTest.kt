package dev.djabari.uniremote.feature.touchpad

import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.model.PointerDelta
import org.junit.Test

class MotionBufferTest {

    private val buffer = MotionBuffer()

    @Test
    fun `sums every delta added between drains`() {
        buffer.add(PointerDelta(3, 4))
        buffer.add(PointerDelta(5, -2))
        buffer.add(PointerDelta(1, 1))

        assertThat(buffer.drain()).isEqualTo(PointerDelta(9, 3))
        assertThat(buffer.drain()).isNull()
    }

    @Test
    fun `carries motion beyond one report into the next drain`() {
        buffer.add(PointerDelta(100, 0))
        buffer.add(PointerDelta(100, 0))

        assertThat(buffer.drain()).isEqualTo(PointerDelta(127, 0))
        assertThat(buffer.drain()).isEqualTo(PointerDelta(73, 0))
        assertThat(buffer.drain()).isNull()
    }

    @Test
    fun `returns null when nothing is pending`() {
        assertThat(buffer.drain()).isNull()
    }
}
