package dev.djabari.tvremote.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import dev.djabari.tvremote.model.PointerDelta
import kotlin.math.abs
import kotlin.math.sign

/**
 * Touchpad surface.
 *
 * Two things make or break the feel:
 *
 *  1. **Rate limiting.** One HID report per motion event will saturate the BT link and the
 *     cursor turns to syrup. Deltas accumulate and flush on a fixed ~60-100Hz cadence
 *     through a conflated channel owned by the ViewModel.
 *  2. **Sub-unit accumulation.** int8 quantisation throws away slow drags entirely unless
 *     the fractional remainder carries to the next frame. [PointerAccumulator] does that.
 */
@Composable
fun TouchpadSurface(
    onDelta: (PointerDelta) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    sensitivity: Float = 1.6f,
) {
    val accumulator = remember { PointerAccumulator() }
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(sensitivity) {
                detectDragGestures(
                    onDragEnd = { accumulator.reset() },
                ) { change, dragAmount ->
                    change.consume()
                    accumulator.add(dragAmount.x, dragAmount.y, sensitivity)?.let(onDelta)
                }
            },
    )
    // TODO(phase3): detectTapGestures for tap -> left click, two-finger tap -> right click,
    //   two-finger vertical drag -> wheel. Keep them in one pointerInput to avoid conflicts.
}

/**
 * Accelerates and quantises raw drag deltas into int8 HID units, keeping the remainder
 * so small movements are not silently dropped.
 */
class PointerAccumulator {
    private var remX = 0f
    private var remY = 0f

    fun add(dx: Float, dy: Float, sensitivity: Float): PointerDelta? {
        remX += dx * accelerate(dx, sensitivity)
        remY += dy * accelerate(dy, sensitivity)

        val outX = remX.toInt()
        val outY = remY.toInt()
        if (outX == 0 && outY == 0) return null

        remX -= outX
        remY -= outY
        return PointerDelta.clamped(outX, outY)
    }

    fun reset() { remX = 0f; remY = 0f }

    /** Mild quadratic acceleration: precise when slow, fast when flicked. */
    private fun accelerate(delta: Float, sensitivity: Float): Float {
        val speed = abs(delta)
        return sensitivity * (1f + speed / 24f).coerceAtMost(3f)
    }
}
