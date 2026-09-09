package dev.djabari.uniremote.transport.network.adapters

import android.util.Base64
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.network.BrandAdapter
import dev.djabari.uniremote.transport.network.LanHttpClients
import dev.djabari.uniremote.transport.network.NetworkCredentials
import dev.djabari.uniremote.transport.network.wol.WakeOnLan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Samsung Tizen remote-control WebSocket (`wss://<ip>:8002`).
 *
 * The first connection makes the TV show an "allow this device" prompt; accepting it returns
 * a token that is persisted per target, so later connections are silent. Pointer control is
 * deliberately absent — Tizen's touchpad channel is a different, undocumented protocol, and
 * the Bluetooth HID transport already covers pointing far better.
 */
class SamsungTizenAdapter(
    private val credentials: NetworkCredentials,
    private val client: OkHttpClient = LanHttpClients.selfSignedTolerant(),
    private val port: Int = 8002,
    private val deviceName: String = "UniRemote",
) : BrandAdapter {

    override val brand = TvBrand.SAMSUNG_TIZEN

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

    private var socket: WebSocket? = null

    override suspend fun discover(): List<RemoteTarget> = emptyList()

    override suspend fun connect(target: RemoteTarget): Result<Unit> {
        val ip = target.ipAddress
            ?: return Result.failure(IllegalArgumentException("Tizen target requires an IP address"))

        disconnectSocket()

        val token = credentials.get(target.id, TOKEN_KEY)
        val name = Base64.encodeToString(deviceName.toByteArray(), Base64.NO_WRAP)
        val url = buildString {
            append("wss://$ip:$port/api/v2/channels/samsung.remote.control?name=$name")
            if (token != null) append("&token=$token")
        }

        val handshake = CompletableDeferred<Result<String?>>()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (json.optString("event") == "ms.channel.connect") {
                    val issued = json.optJSONObject("data")?.optString("token")?.ifBlank { null }
                    handshake.complete(Result.success(issued))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handshake.complete(Result.failure(t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
            }
        }

        val ws = client.newWebSocket(Request.Builder().url(url).build(), listener)

        return try {
            val result = withTimeout(HANDSHAKE_TIMEOUT_MS) { handshake.await() }
            result.map { issuedToken ->
                if (issuedToken != null && issuedToken != token) {
                    credentials.put(target.id, TOKEN_KEY, issuedToken)
                }
                socket = ws
            }.onFailure { ws.cancel() }
        } catch (_: TimeoutCancellationException) {
            ws.cancel()
            Result.failure(IllegalStateException("TV did not accept the connection. Approve the prompt on screen and retry."))
        }
    }

    override suspend fun send(key: RemoteKey): Result<Unit> {
        val ws = socket ?: return Result.failure(IllegalStateException("Not connected"))
        val name = TizenKeys[key]
            ?: return Result.failure(IllegalArgumentException("$key is not available on Tizen"))

        val payload = JSONObject()
            .put("method", "ms.remote.control")
            .put(
                "params",
                JSONObject()
                    .put("Cmd", "Click")
                    .put("DataOfCmd", name)
                    .put("Option", "false")
                    .put("TypeOfRemote", "SendRemoteKey"),
            )

        return if (ws.send(payload.toString())) Result.success(Unit)
        else Result.failure(IllegalStateException("WebSocket rejected the key"))
    }

    override suspend fun sendText(text: String): Result<Unit> {
        val ws = socket ?: return Result.failure(IllegalStateException("Not connected"))
        val payload = JSONObject()
            .put("method", "ms.remote.control")
            .put(
                "params",
                JSONObject()
                    .put("Cmd", Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))
                    .put("DataOfCmd", "base64")
                    .put("TypeOfRemote", "SendInputString"),
            )

        return if (ws.send(payload.toString())) Result.success(Unit)
        else Result.failure(IllegalStateException("WebSocket rejected the text"))
    }

    override suspend fun wakeOnLan(mac: String): Result<Unit> = WakeOnLan.send(mac)

    override suspend fun disconnect() = disconnectSocket()

    private fun disconnectSocket() {
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
    }

    private companion object {
        const val TOKEN_KEY = "tizen_token"
        const val HANDSHAKE_TIMEOUT_MS = 20_000L
        const val NORMAL_CLOSURE = 1000
    }
}
