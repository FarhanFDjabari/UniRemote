package dev.djabari.uniremote.transport.network.adapters

import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.transport.TransportCapability
import org.junit.Test

class RokuAdapterTest {

    private val adapter = RokuAdapter()

    @Test
    fun `capabilities includes dpad, volume, media, and power on`() {
        assertThat(adapter.capabilities).contains(TransportCapability.DPAD)
        assertThat(adapter.capabilities).contains(TransportCapability.VOLUME)
        assertThat(adapter.capabilities).contains(TransportCapability.MEDIA_KEYS)
        assertThat(adapter.capabilities).contains(TransportCapability.POWER_ON)
        assertThat(adapter.capabilities).doesNotContain(TransportCapability.POINTER)
    }

    @Test
    fun `mapKey correctly translates standard remote keys to Roku ECP keypress commands`() {
        assertThat(adapter.mapKey(RemoteKey.DPAD_UP)).isEqualTo("Up")
        assertThat(adapter.mapKey(RemoteKey.DPAD_DOWN)).isEqualTo("Down")
        assertThat(adapter.mapKey(RemoteKey.DPAD_LEFT)).isEqualTo("Left")
        assertThat(adapter.mapKey(RemoteKey.DPAD_RIGHT)).isEqualTo("Right")
        assertThat(adapter.mapKey(RemoteKey.DPAD_CENTER)).isEqualTo("Select")
        assertThat(adapter.mapKey(RemoteKey.BACK)).isEqualTo("Back")
        assertThat(adapter.mapKey(RemoteKey.HOME)).isEqualTo("Home")
        assertThat(adapter.mapKey(RemoteKey.VOLUME_UP)).isEqualTo("VolumeUp")
        assertThat(adapter.mapKey(RemoteKey.VOLUME_DOWN)).isEqualTo("VolumeDown")
        assertThat(adapter.mapKey(RemoteKey.MUTE)).isEqualTo("VolumeMute")
        assertThat(adapter.mapKey(RemoteKey.PLAY_PAUSE)).isEqualTo("Play")
        assertThat(adapter.mapKey(RemoteKey.POWER_OFF)).isEqualTo("PowerOff")
        assertThat(adapter.mapKey(RemoteKey.POWER_ON)).isEqualTo("PowerOn")
    }
}
