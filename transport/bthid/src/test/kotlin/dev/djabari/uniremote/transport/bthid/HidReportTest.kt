package dev.djabari.uniremote.transport.bthid

import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.model.PointerDelta
import dev.djabari.uniremote.model.RemoteKey
import org.junit.Test

class HidReportTest {

    @Test
    fun `keyboard report packs modifier and keycode`() {
        val report = HidReport.keyboard(KeyboardModifier.LEFT_SHIFT, KeyboardUsage.A)
        assertThat(report.toList()).containsExactly(
            0x02.toByte(), 0x00.toByte(), 0x04.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        ).inOrder()
    }

    @Test
    fun `consumer report is little endian`() {
        // Volume Up = 0x00E9
        assertThat(HidReport.consumer(ConsumerUsage.VOLUME_UP).toList())
            .containsExactly(0xE9.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()).inOrder()

        // AC Home = 0x0223 -> spans both bytes, the case that catches endianness bugs
        assertThat(HidReport.consumer(ConsumerUsage.AC_HOME).toList())
            .containsExactly(0x23.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte()).inOrder()
    }

    @Test
    fun `mouse report encodes negative deltas as signed bytes`() {
        val report = HidReport.mouse(buttons = 0, delta = PointerDelta(-5, 12, 0))
        assertThat(report[1]).isEqualTo((-5).toByte())
        assertThat(report[2]).isEqualTo(12.toByte())
    }

    @Test
    fun `numpad mapping is contiguous and 0 sits above 9`() {
        val digits = listOf(
            RemoteKey.NUM_1, RemoteKey.NUM_2, RemoteKey.NUM_3, RemoteKey.NUM_4, RemoteKey.NUM_5,
            RemoteKey.NUM_6, RemoteKey.NUM_7, RemoteKey.NUM_8, RemoteKey.NUM_9,
        ).map { (RemoteKeyMapping[it] as HidBinding.Keyboard).usage }

        assertThat(digits).isEqualTo((0x1E..0x26).toList())
        assertThat((RemoteKeyMapping[RemoteKey.NUM_0] as HidBinding.Keyboard).usage).isEqualTo(0x27)
    }

    @Test
    fun `volume uses the consumer page not the keyboard page`() {
        // TVs widely ignore keyboard volume keycodes. Regression guard.
        assertThat(RemoteKeyMapping[RemoteKey.VOLUME_UP]).isInstanceOf(HidBinding.Consumer::class.java)
        assertThat(RemoteKeyMapping[RemoteKey.VOLUME_DOWN]).isInstanceOf(HidBinding.Consumer::class.java)
        assertThat(RemoteKeyMapping[RemoteKey.MUTE]).isInstanceOf(HidBinding.Consumer::class.java)
    }

    @Test
    fun `power on has no HID binding`() {
        // A TV with its radio off cannot receive HID. If this ever passes, someone has
        // added a binding that will silently do nothing.
        assertThat(RemoteKeyMapping[RemoteKey.POWER_ON]).isNull()
    }

    @Test
    fun `ascii map covers printable ascii and rejects everything else`() {
        (0x20..0x7E).forEach { code ->
            assertThat(AsciiKeyMap.stroke(code.toChar())).isNotNull()
        }
        assertThat(AsciiKeyMap.stroke('é')).isNull()
        assertThat(AsciiKeyMap.untypeable("hello 世界")).containsExactly('世', '界')
    }
}
