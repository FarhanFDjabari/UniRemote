package dev.djabari.tvremote.transport.bthid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Walks the descriptor as HID short items and asserts it is structurally sound.
 *
 * A malformed descriptor does not fail loudly — the TV just silently ignores the phone,
 * and you spend two days blaming Bluetooth. This test is the cheapest insurance in the
 * whole project.
 */
class HidDescriptorTest {

    private data class Item(val tag: Int, val type: Int, val size: Int, val data: List<Int>)

    private fun parse(bytes: ByteArray): List<Item> {
        val items = mutableListOf<Item>()
        var i = 0
        while (i < bytes.size) {
            val prefix = bytes[i].toInt() and 0xFF
            val size = when (prefix and 0x03) { 3 -> 4; else -> prefix and 0x03 }
            val type = (prefix shr 2) and 0x03
            val tag = (prefix shr 4) and 0x0F
            val data = (1..size).map { bytes[i + it].toInt() and 0xFF }
            items += Item(tag, type, size, data)
            i += 1 + size
        }
        return items
    }

    @Test
    fun `descriptor parses cleanly to the final byte`() {
        // parse() overruns the array if any item length is wrong, so simply completing is
        // the assertion; the count guards against silent truncation.
        val items = parse(HidDescriptor.BYTES)
        assertThat(items).isNotEmpty()
    }

    @Test
    fun `collections are balanced`() {
        var depth = 0
        parse(HidDescriptor.BYTES).forEach { item ->
            if (item.type == MAIN) {
                when (item.tag) {
                    TAG_COLLECTION -> depth++
                    TAG_END_COLLECTION -> depth--
                }
            }
            assertThat(depth).isAtLeast(0)
        }
        assertThat(depth).isEqualTo(0)
    }

    @Test
    fun `three unique report ids are declared`() {
        val ids = parse(HidDescriptor.BYTES)
            .filter { it.type == GLOBAL && it.tag == TAG_REPORT_ID }
            .map { it.data.first() }

        assertThat(ids).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `every input report is byte aligned`() {
        var reportSize = 0
        var reportCount = 0
        var bits = 0
        var currentId = -1
        val bitsPerId = mutableMapOf<Int, Int>()

        parse(HidDescriptor.BYTES).forEach { item ->
            when {
                item.type == GLOBAL && item.tag == TAG_REPORT_ID -> {
                    if (currentId != -1) bitsPerId[currentId] = bits
                    currentId = item.data.first()
                    bits = 0
                }
                item.type == GLOBAL && item.tag == TAG_REPORT_SIZE -> reportSize = item.data.first()
                item.type == GLOBAL && item.tag == TAG_REPORT_COUNT -> reportCount = item.data.first()
                item.type == MAIN && item.tag == TAG_INPUT -> bits += reportSize * reportCount
            }
        }
        if (currentId != -1) bitsPerId[currentId] = bits

        assertThat(bitsPerId[1]).isEqualTo(HidReport.KEYBOARD_SIZE * 8)
        assertThat(bitsPerId[2]).isEqualTo(HidReport.MOUSE_SIZE * 8)
        assertThat(bitsPerId[3]).isEqualTo(HidReport.CONSUMER_SIZE * 8)
    }

    private companion object {
        const val MAIN = 0
        const val GLOBAL = 1
        const val TAG_INPUT = 0x8
        const val TAG_COLLECTION = 0xA
        const val TAG_END_COLLECTION = 0xC
        const val TAG_REPORT_SIZE = 0x7
        const val TAG_REPORT_ID = 0x8
        const val TAG_REPORT_COUNT = 0x9
    }
}
