package dev.djabari.uniremote.transport.network.adapters

import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.model.KeyAction
import org.json.JSONObject
import org.junit.Test

class SamsungTizenAdapterTest {

    private fun params(action: KeyAction): JSONObject =
        JSONObject(SamsungTizenAdapter.remoteKeyPayload("KEY_ENTER", action)).getJSONObject("params")

    @Test
    fun `a tap is a Click and a hold is Press then Release`() {
        assertThat(params(KeyAction.TAP).getString("Cmd")).isEqualTo("Click")
        assertThat(params(KeyAction.PRESS).getString("Cmd")).isEqualTo("Press")
        assertThat(params(KeyAction.RELEASE).getString("Cmd")).isEqualTo("Release")
    }

    @Test
    fun `key payload targets the remote control channel with the key name`() {
        val payload = JSONObject(SamsungTizenAdapter.remoteKeyPayload("KEY_ENTER", KeyAction.PRESS))

        assertThat(payload.getString("method")).isEqualTo("ms.remote.control")
        assertThat(payload.getJSONObject("params").getString("DataOfCmd")).isEqualTo("KEY_ENTER")
        assertThat(payload.getJSONObject("params").getString("TypeOfRemote")).isEqualTo("SendRemoteKey")
    }
}
