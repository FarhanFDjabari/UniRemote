package dev.djabari.uniremote.session

import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.model.RemoteTarget
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
            ),
            RemoteTarget(
                id = "11:22:33:44:55:66",
                displayName = "Bedroom",
                brand = TvBrand.SAMSUNG_TIZEN,
                bluetoothAddress = "11:22:33:44:55:66",
            ),
        )

        assertThat(TargetCodec.decode(TargetCodec.encode(targets))).isEqualTo(targets)
    }

    @Test
    fun `preserves names containing delimiter characters`() {
        val target = RemoteTarget(
            id = "AA:BB:CC:DD:EE:01",
            displayName = "Dad's TV || kitchen ;; \"main\"",
            brand = TvBrand.SAMSUNG_TIZEN,
            bluetoothAddress = "AA:BB:CC:DD:EE:01",
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
        val raw = """[{"id":"1","displayName":"Future TV","brand":"HOLOGRAM","bluetoothAddress":"AA:BB:CC:DD:EE:FF"}]"""

        assertThat(TargetCodec.decode(raw).single().brand).isEqualTo(TvBrand.UNKNOWN)
    }

    @Test
    fun `drops targets saved by the removed Wi-Fi transport`() {
        val raw = """[
            {"id":"192.168.1.24","displayName":"Roku","brand":"ROKU","ipAddress":"192.168.1.24"},
            {"id":"AA:BB:CC:DD:EE:FF","displayName":"Living room","brand":"ANDROID_TV",
             "bluetoothAddress":"AA:BB:CC:DD:EE:FF","ipAddress":null,"macAddress":null,"preferredTransport":"bt-hid"}
        ]"""

        val decoded = TargetCodec.decode(raw)

        assertThat(decoded).containsExactly(
            RemoteTarget(
                id = "AA:BB:CC:DD:EE:FF",
                displayName = "Living room",
                brand = TvBrand.ANDROID_TV,
                bluetoothAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
    }
}
