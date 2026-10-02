package dev.djabari.uniremote.feature.touchpad

import dev.djabari.uniremote.model.PointerDelta

/**
 * Sums relative pointer motion between flushes. Every delta must reach the TV — dropping
 * any of them makes the cursor stutter and under-travel — but one HID report can carry at
 * most ±127 per axis, so whatever does not fit is kept for the next flush.
 */
internal class MotionBuffer {

    private val lock = Any()
    private var pendingX = 0
    private var pendingY = 0

    fun add(delta: PointerDelta) = synchronized(lock) {
        pendingX += delta.dx
        pendingY += delta.dy
    }

    fun drain(): PointerDelta? = synchronized(lock) {
        if (pendingX == 0 && pendingY == 0) return@synchronized null

        val delta = PointerDelta.clamped(pendingX, pendingY)
        pendingX -= delta.dx
        pendingY -= delta.dy
        delta
    }
}
