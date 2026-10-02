package dev.djabari.uniremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.djabari.uniremote.model.PointerDelta
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Touchpad surface supporting multi-touch gestures:
 * - Single-finger drag: cursor motion with quadratic acceleration and sub-unit accumulation
 * - Single-finger tap: left click
 * - Two-finger tap: right click / context menu
 * - Two-finger drag: scroll wheel
 */
@Composable
fun TouchpadSurface(
    onDelta: (PointerDelta) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onSecondaryTap: (() -> Unit)? = null,
    onScroll: ((Int) -> Unit)? = null,
    sensitivity: Float = 1.6f,
) {
    val accumulator = remember { PointerAccumulator() }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .pointerInput(sensitivity) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val startTime = System.currentTimeMillis()
                    var maxPointers = 1
                    var totalDragDistance = 0f
                    var isDrag = false

                    accumulator.reset()

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes
                        val activePointers = pointers.filter { it.pressed }

                        if (activePointers.size > maxPointers) {
                            maxPointers = activePointers.size
                        }

                        if (activePointers.isEmpty()) {
                            // Touch release
                            val duration = System.currentTimeMillis() - startTime
                            if (!isDrag && duration < 350 && totalDragDistance < 20f) {
                                if (maxPointers >= 2 && onSecondaryTap != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSecondaryTap()
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTap()
                                }
                            }
                            accumulator.reset()
                            break
                        }

                        if (activePointers.size == 1) {
                            val change = activePointers.first()
                            val delta = change.position - change.previousPosition
                            totalDragDistance += delta.getDistance()

                            if (totalDragDistance > 10f) {
                                isDrag = true
                            }

                            if (isDrag) {
                                change.consume()
                                accumulator.add(delta.x, delta.y, sensitivity)?.let(onDelta)
                            }
                        } else if (activePointers.size >= 2) {
                            // Two-finger scroll
                            val change1 = activePointers[0]
                            val change2 = activePointers[1]
                            val avgDeltaY = ((change1.position.y - change1.previousPosition.y) +
                                (change2.position.y - change2.previousPosition.y)) / 2f
                            totalDragDistance += abs(avgDeltaY)

                            if (totalDragDistance > 10f) {
                                isDrag = true
                            }

                            if (isDrag && abs(avgDeltaY) > 2f) {
                                change1.consume()
                                change2.consume()
                                val scrollTicks = -(avgDeltaY / 15f).toInt().coerceIn(-127, 127)
                                if (scrollTicks != 0) {
                                    onScroll?.invoke(scrollTicks)
                                }
                            }
                        }
                    }
                }
            },
    )
}

/**
 * Accelerates and quantises raw drag deltas into int8 HID units, keeping the remainder
 * so small movements are not silently dropped.
 */
class PointerAccumulator {
    private var remX = 0f
    private var remY = 0f

    fun add(dx: Float, dy: Float, sensitivity: Float): PointerDelta? {
        val factor = accelerate(hypot(dx, dy), sensitivity)
        remX += dx * factor
        remY += dy * factor

        val outX = remX.toInt()
        val outY = remY.toInt()
        if (outX == 0 && outY == 0) return null

        remX -= outX
        remY -= outY
        return PointerDelta.clamped(outX, outY)
    }

    fun reset() {
        remX = 0f
        remY = 0f
    }

    /** Mild quadratic acceleration: precise when slow, fast when flicked. */
    private fun accelerate(speed: Float, sensitivity: Float): Float =
        sensitivity * (1f + speed / 24f).coerceAtMost(3f)
}
