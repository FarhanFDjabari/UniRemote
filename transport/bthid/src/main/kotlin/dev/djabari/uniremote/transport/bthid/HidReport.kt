package dev.djabari.uniremote.transport.bthid

import dev.djabari.uniremote.model.PointerDelta

/**
 * Pure byte-packing for the three report types. No Android dependencies, no I/O —
 * every function here is covered by JVM unit tests, which is where the real bugs live.
 */
object HidReport {

    const val KEYBOARD_SIZE = 8
    const val MOUSE_SIZE = 4
    const val CONSUMER_SIZE = 4

    val KEYBOARD_RELEASE: ByteArray = ByteArray(KEYBOARD_SIZE)
    val CONSUMER_RELEASE: ByteArray = ByteArray(CONSUMER_SIZE)

    /**
     * @param modifiers bitmask from [KeyboardModifier]
     * @param keycodes  up to 6 simultaneous usages from [KeyboardUsage]
     */
    fun keyboard(modifiers: Int, vararg keycodes: Int): ByteArray {
        require(keycodes.size <= 6) { "HID boot keyboard supports at most 6 concurrent keys" }
        val report = ByteArray(KEYBOARD_SIZE)
        report[0] = modifiers.toByte()
        report[1] = 0 // reserved, always zero
        keycodes.forEachIndexed { i, code -> report[2 + i] = code.toByte() }
        return report
    }

    /** Buttons bitmask: bit0 = left, bit1 = right, bit2 = middle. */
    fun mouse(buttons: Int, delta: PointerDelta): ByteArray = byteArrayOf(
        (buttons and 0x07).toByte(),
        delta.dx.toByte(),
        delta.dy.toByte(),
        delta.wheel.toByte(),
    )

    /** Consumer reports carry an *array* of up to two 16-bit usages, little-endian. */
    fun consumer(usage: Int, secondUsage: Int = ConsumerUsage.NONE): ByteArray = byteArrayOf(
        (usage and 0xFF).toByte(),
        ((usage shr 8) and 0xFF).toByte(),
        (secondUsage and 0xFF).toByte(),
        ((secondUsage shr 8) and 0xFF).toByte(),
    )
}
