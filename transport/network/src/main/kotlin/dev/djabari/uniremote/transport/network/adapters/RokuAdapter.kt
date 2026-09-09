package dev.djabari.uniremote.transport.network.adapters

import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.network.BrandAdapter
import dev.djabari.uniremote.transport.network.wol.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Roku External Control Protocol (ECP) adapter.
 *
 * Communicates via HTTP over port 8060 without requiring authentication or pairing codes.
 */
class RokuAdapter(
    private val port: Int = 8060,
) : BrandAdapter {

    override val brand = TvBrand.ROKU

    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DPAD,
        TransportCapability.NUMPAD,
        TransportCapability.VOLUME,
        TransportCapability.CHANNEL,
        TransportCapability.MEDIA_KEYS,
        TransportCapability.TEXT_INPUT,
        TransportCapability.UNICODE_TEXT,
        TransportCapability.POWER_OFF,
        TransportCapability.POWER_ON,
    )

    private var activeTarget: RemoteTarget? = null

    override suspend fun discover(): List<RemoteTarget> = withContext(Dispatchers.IO) {
        // SSDP M-SEARCH "roku:ecp" discovery can be queried here
        emptyList()
    }

    override suspend fun connect(target: RemoteTarget): Result<Unit> = withContext(Dispatchers.IO) {
        val ip = target.ipAddress
            ?: return@withContext Result.failure(IllegalArgumentException("Roku target requires an IP address"))
        if (!probe(ip)) {
            return@withContext Result.failure(IllegalStateException("Roku ECP did not answer at $ip"))
        }
        activeTarget = target
        Result.success(Unit)
    }

    override suspend fun identify(target: RemoteTarget): Boolean = withContext(Dispatchers.IO) {
        val ip = target.ipAddress ?: return@withContext false
        probe(ip)
    }

    /** Silent GET that only succeeds against a live Roku ECP endpoint. No side effects. */
    private fun probe(ip: String): Boolean = runCatching {
        val url = URL("http://$ip:$port/query/device-info")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 3000
            requestMethod = "GET"
        }
        try {
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    override suspend fun send(key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        val ip = activeTarget?.ipAddress
            ?: return@withContext Result.failure(IllegalStateException("No connected Roku target"))

        val command = mapKey(key)
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported Roku key: $key"))

        postCommand(ip, "keypress/$command")
    }

    override suspend fun sendText(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ip = activeTarget?.ipAddress
            ?: return@withContext Result.failure(IllegalStateException("No connected Roku target"))

        runCatching {
            for (ch in text) {
                val encoded = URLEncoder.encode(ch.toString(), "UTF-8")
                val result = postCommand(ip, "keypress/Lit_$encoded")
                if (result.isFailure) throw result.exceptionOrNull()!!
            }
        }
    }

    override suspend fun disconnect() {
        activeTarget = null
    }

    override suspend fun wakeOnLan(mac: String): Result<Unit> =
        WakeOnLan.send(mac)

    private fun postCommand(ip: String, path: String): Result<Unit> = runCatching {
        val url = URL("http://$ip:$port/$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 2000
            readTimeout = 2000
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(0)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP error $code from Roku ECP")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun mapKey(key: RemoteKey): String? = when (key) {
        RemoteKey.DPAD_UP -> "Up"
        RemoteKey.DPAD_DOWN -> "Down"
        RemoteKey.DPAD_LEFT -> "Left"
        RemoteKey.DPAD_RIGHT -> "Right"
        RemoteKey.DPAD_CENTER -> "Select"
        RemoteKey.BACK -> "Back"
        RemoteKey.HOME -> "Home"
        RemoteKey.MENU -> "Info"

        RemoteKey.VOLUME_UP -> "VolumeUp"
        RemoteKey.VOLUME_DOWN -> "VolumeDown"
        RemoteKey.MUTE -> "VolumeMute"

        RemoteKey.CHANNEL_UP -> "ChannelUp"
        RemoteKey.CHANNEL_DOWN -> "ChannelDown"

        RemoteKey.NUM_0 -> "Lit_0"
        RemoteKey.NUM_1 -> "Lit_1"
        RemoteKey.NUM_2 -> "Lit_2"
        RemoteKey.NUM_3 -> "Lit_3"
        RemoteKey.NUM_4 -> "Lit_4"
        RemoteKey.NUM_5 -> "Lit_5"
        RemoteKey.NUM_6 -> "Lit_6"
        RemoteKey.NUM_7 -> "Lit_7"
        RemoteKey.NUM_8 -> "Lit_8"
        RemoteKey.NUM_9 -> "Lit_9"

        RemoteKey.PLAY_PAUSE -> "Play"
        RemoteKey.STOP -> "Stop"
        RemoteKey.FAST_FORWARD -> "Fwd"
        RemoteKey.REWIND -> "Rev"
        RemoteKey.NEXT_TRACK -> "Fwd"
        RemoteKey.PREV_TRACK -> "Rev"

        RemoteKey.POWER_OFF -> "PowerOff"
        RemoteKey.POWER_ON -> "PowerOn"

        RemoteKey.BACKSPACE -> "Backspace"
        RemoteKey.ENTER -> "Enter"
        RemoteKey.SPACE -> "Lit_%20"
        RemoteKey.TAB -> null
    }
}
