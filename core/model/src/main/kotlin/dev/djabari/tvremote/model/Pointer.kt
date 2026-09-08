package dev.djabari.tvremote.model

/**
 * A relative pointer movement plus optional scroll, already quantised for the wire.
 *
 * HID mouse reports carry signed 8-bit relative axes, so these are clamped to -127..127
 * at construction. The touchpad accumulates sub-unit remainders itself so small slow drags
 * are not silently discarded.
 */
data class PointerDelta(
    val dx: Int,
    val dy: Int,
    val wheel: Int = 0,
) {
    init {
        require(dx in RANGE && dy in RANGE && wheel in RANGE) {
            "PointerDelta components must be in $RANGE, got ($dx, $dy, $wheel)"
        }
    }

    val isIdle: Boolean get() = dx == 0 && dy == 0 && wheel == 0

    companion object {
        val RANGE = -127..127
        val IDLE = PointerDelta(0, 0, 0)

        /** Clamps instead of throwing. Use from the gesture layer. */
        fun clamped(dx: Int, dy: Int, wheel: Int = 0) = PointerDelta(
            dx.coerceIn(RANGE),
            dy.coerceIn(RANGE),
            wheel.coerceIn(RANGE),
        )
    }
}

enum class PointerButton { LEFT, RIGHT, MIDDLE }

sealed interface PointerEvent {
    data class Move(val delta: PointerDelta) : PointerEvent
    data class Button(val button: PointerButton, val pressed: Boolean) : PointerEvent
    data class Scroll(val ticks: Int) : PointerEvent
}
