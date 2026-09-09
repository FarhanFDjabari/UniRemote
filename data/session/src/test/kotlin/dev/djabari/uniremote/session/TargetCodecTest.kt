package dev.djabari.uniremote.session

import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TransportId
import dev.djabari.uniremote.model.TvBrand
import org.junit.Test

class TargetCodecTest {

    @Test
    fun `round trips every field`() {
        val targets = listOf(
            RemoteTarget(
                id = "AA:BB:CC:DD:EE:FF",
                displayName = "Living room",
                brand = TvBrand.ANDROID_TV,
                bluetoothAddress = "AA:BB:CC:DD:EE:FF",
                preferredTransport = TransportId.BLUETOOTH_HID,
            ),
            RemoteTarget(
                id = "192.168.1.24",
                displayName = "Roku",
                brand = TvBrand.ROKU,
                ipAddress = "192.168.1.24",
                macAddress = "00:11:22:33:44:55",
            ),
        )

        assertThat(TargetCodec.decode(TargetCodec.encode(targets))).isEqualTo(targets)
    }

    @Test
    fun `preserves names containing delimiter characters`() {
        val target = RemoteTarget(
            id = "192.168.1.5",
            displayName = "Dad's TV || kitchen ;; \"main\"",
            brand = TvBrand.SAMSUNG_TIZEN,
            ipAddress = "192.168.1.5",
        )

        val decoded = TargetCodec.decode(TargetCodec.encode(listOf(target)))

        assertThat(decoded).containsExactly(target)
    }

    @Test
    fun `returns empty list for blank or corrupt data`() {
        assertThat(TargetCodec.decode("")).isEmpty()
        assertThat(TargetCodec.decode("not json")).isEmpty()
    }

    @Test
    fun `falls back to UNKNOWN for a brand written by a newer version`() {
        val raw = """[{"id":"1","displayName":"Future TV","brand":"HOLOGRAM"}]"""

        assertThat(TargetCodec.decode(raw).single().brand).isEqualTo(TvBrand.UNKNOWN)
    }
}
