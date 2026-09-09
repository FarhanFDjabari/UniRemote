package dev.djabari.uniremote.transport.network.adapters

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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * LG webOS SSAP adapter (`ws://<ip>:3000`).
 *
 * Registration returns a client key that is stored per target; without it the TV re-prompts
 * on every connect. Navigation keys do not exist as SSAP requests — they go over the
 * separate pointer input socket the TV hands out on request, which is why this adapter
 * holds two sockets.
 */
class LgWebOsAdapter(
    private val credentials: NetworkCredentials,
    private val client: OkHttpClient = LanHttpClients.strict(),
    private val port: Int = 3000,
) : BrandAdapter {

    override val brand = TvBrand.LG_WEBOS

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

    private val requestId = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private var socket: WebSocket? = null
    private var pointerSocket: WebSocket? = null

    override suspend fun discover(): List<RemoteTarget> = emptyList()

    override suspend fun connect(target: RemoteTarget): Result<Unit> {
        val ip = target.ipAddress
            ?: return Result.failure(IllegalArgumentException("webOS target requires an IP address"))

        disconnectSockets()

        val storedKey = credentials.get(target.id, CLIENT_KEY)
        val registered = CompletableDeferred<Result<String?>>()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (json.optString("type")) {
                    "registered" -> registered.complete(
                        Result.success(json.optJSONObject("payload")?.optString(CLIENT_KEY)?.ifBlank { null }),
                    )
                    "error" -> {
                        val message = json.optString("error").ifBlank { "webOS rejected the request" }
                        if (!registered.isCompleted) registered.complete(Result.failure(IllegalStateException(message)))
                        json.optString("id").takeIf { it.isNotBlank() }
                            ?.let { pending.remove(it)?.completeExceptionally(IllegalStateException(message)) }
                    }
                    "response" -> json.optString("id").takeIf { it.isNotBlank() }
                        ?.let { id -> pending.remove(id)?.complete(json.optJSONObject("payload") ?: JSONObject()) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!registered.isCompleted) registered.complete(Result.failure(t))
                failPending(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
            }
        }

        val ws = client.newWebSocket(Request.Builder().url("ws://$ip:$port").build(), listener)
        ws.send(registrationPayload(storedKey).toString())

        return try {
            val result = withTimeout(REGISTRATION_TIMEOUT_MS) { registered.await() }
            result.mapCatching { issuedKey ->
                if (issuedKey != null && issuedKey != storedKey) {
                    credentials.put(target.id, CLIENT_KEY, issuedKey)
                }
                socket = ws
                openPointerSocket()
            }.onFailure {
                ws.cancel()
                disconnectSockets()
            }
        } catch (_: TimeoutCancellationException) {
            ws.cancel()
            Result.failure(IllegalStateException("TV did not confirm pairing. Accept the prompt on screen and retry."))
        }
    }

    override suspend fun send(key: RemoteKey): Result<Unit> {
        WebOsKeys.button(key)?.let { button ->
            val pointer = pointerSocket
                ?: return Result.failure(IllegalStateException("Pointer input socket is not open"))
            return if (pointer.send("type:button\nname:$button\n\n")) Result.success(Unit)
            else Result.failure(IllegalStateException("Pointer socket rejected the button"))
        }

        val uri = WebOsKeys.uri(key)
            ?: return Result.failure(IllegalArgumentException("$key is not available on webOS"))

        val payload = if (key == RemoteKey.MUTE) JSONObject().put("mute", true) else null
        return runCatching { request(uri, payload) }.map { }
    }

    override suspend fun sendText(text: String): Result<Unit> =
        runCatching {
            request(
                "ssap://com.webos.service.ime/insertText",
                JSONObject().put("text", text).put("replace", 0),
            )
        }.map { }

    override suspend fun wakeOnLan(mac: String): Result<Unit> = WakeOnLan.send(mac)

    override suspend fun disconnect() = disconnectSockets()

    private suspend fun openPointerSocket() {
        val payload = request("ssap://com.webos.service.networkinput/getPointerInputSocket")
        val path = payload.optString("socketPath").ifBlank { return }
        pointerSocket = client.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {})
    }

    private suspend fun request(uri: String, payload: JSONObject? = null): JSONObject {
        val ws = socket ?: throw IllegalStateException("Not connected")
        val id = "req_${requestId.incrementAndGet()}"
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred

        val message = JSONObject()
            .put("type", "request")
            .put("id", id)
            .put("uri", uri)
        if (payload != null) message.put("payload", payload)

        if (!ws.send(message.toString())) {
            pending.remove(id)
            throw IllegalStateException("WebSocket rejected the request")
        }

        return try {
            withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            pending.remove(id)
            throw IllegalStateException("TV did not answer $uri")
        }
    }

    private fun failPending(cause: Throwable) {
        pending.values.forEach { it.completeExceptionally(cause) }
        pending.clear()
    }

    private fun disconnectSockets() {
        pointerSocket?.close(NORMAL_CLOSURE, null)
        pointerSocket = null
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        failPending(IllegalStateException("Disconnected"))
    }

    private fun registrationPayload(clientKey: String?): JSONObject {
        val manifest = JSONObject()
            .put("manifestVersion", 1)
            .put("appVersion", "1.0")
            .put("signed", JSONObject().put("appId", APP_ID).put("vendorId", "dev.djabari"))
            .put(
                "permissions",
                JSONArray(
                    listOf(
                        "CONTROL_INPUT_TEXT",
                        "CONTROL_INPUT_MEDIA_PLAYBACK",
                        "CONTROL_INPUT_TV",
                        "CONTROL_POWER",
                        "CONTROL_AUDIO",
                        "READ_INSTALLED_APPS",
                        "LAUNCH",
                    ),
                ),
            )

        val payload = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", manifest)
        if (clientKey != null) payload.put(CLIENT_KEY, clientKey)

        return JSONObject()
            .put("type", "register")
            .put("id", "register_0")
            .put("payload", payload)
    }

    private companion object {
        const val CLIENT_KEY = "client-key"
        const val APP_ID = "dev.djabari.uniremote"
        const val REGISTRATION_TIMEOUT_MS = 30_000L
        const val REQUEST_TIMEOUT_MS = 5_000L
        const val NORMAL_CLOSURE = 1000
    }
}
