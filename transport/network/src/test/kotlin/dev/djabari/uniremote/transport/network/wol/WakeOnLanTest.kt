package dev.djabari.uniremote.transport.network.wol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WakeOnLanTest {

    @Test
    fun `buildMagicPacket creates exactly 102 bytes with 6 0xFF and 16 MAC repetitions`() {
        val mac = "AA:BB:CC:DD:EE:FF"
        val packet = WakeOnLan.buildMagicPacket(mac)

        assertThat(packet.size).isEqualTo(102)

        // First 6 bytes are 0xFF
        for (i in 0 until 6) {
            assertThat(packet[i]).isEqualTo(0xFF.toByte())
        }

        // Next 16 * 6 = 96 bytes are repeated MAC bytes
        val expectedMacBytes = byteArrayOf(
            0xAA.toByte(),
            0xBB.toByte(),
            0xCC.toByte(),
            0xDD.toByte(),
            0xEE.toByte(),
            0xFF.toByte(),
        )

        for (repetition in 0 until 16) {
            val offset = 6 + repetition * 6
            val slice = packet.sliceArray(offset until offset + 6)
            assertThat(slice).isEqualTo(expectedMacBytes)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildMagicPacket throws on invalid MAC length`() {
        WakeOnLan.buildMagicPacket("AA:BB:CC")
    }
}
