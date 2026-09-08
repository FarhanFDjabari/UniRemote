package dev.djabari.tvremote.transport.network

import dev.djabari.tvremote.model.*
import dev.djabari.tvremote.transport.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PHASE 4. Fallback for TVs that reject the BT HID role (Roku) and for cold power-on,
 * which Bluetooth physically cannot do.
 *
 * One [BrandAdapter] per ecosystem behind a single transport, so the rest of the app
 * still sees exactly one [RemoteTransport]. This is also where the pairing codes you
 * wanted to avoid live — confined to this module and only for the brands that demand them.
 */
class NetworkTransport(
    private val adapters: List<BrandAdapter>,
) : RemoteTransport {

    override val id = TransportId.NETWORK

    private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = _state

    private val _capabilities = MutableStateFlow<Set<TransportCapability>>(emptySet())
    override val capabilities: StateFlow<Set<TransportCapability>> = _capabilities

    private var active: BrandAdapter? = null

    override fun supports(target: RemoteTarget) = adapters.any { it.brand == target.brand }

    override suspend fun connect(target: RemoteTarget): Result<Unit> =
        Result.failure(NotImplementedError("phase 4"))

    override suspend fun disconnect() { active = null; _state.value = TransportState.Idle }

    override suspend fun sendKey(key: RemoteKey, action: KeyAction) =
        Result.failure<Unit>(NotImplementedError("phase 4"))

    override suspend fun sendText(text: String) =
        Result.failure<Unit>(NotImplementedError("phase 4"))

    override suspend fun sendPointer(event: PointerEvent) =
        Result.failure<Unit>(NotImplementedError("phase 4"))
}

/**
 * Per-ecosystem protocol adapter.
 *
 *  ANDROID_TV     mDNS `_androidtvremote2._tcp` + TLS, one-time 6-digit pairing code
 *  SAMSUNG_TIZEN  WebSocket wss://<ip>:8002, on-TV allow prompt, token persisted
 *  LG_WEBOS       WebSocket ws://<ip>:3000, client-key handshake
 *  ROKU           ECP over plain HTTP :8060, no auth at all
 *
 * Power-on for all four is Wake-on-LAN to [RemoteTarget.macAddress], not a protocol call.
 */
interface BrandAdapter {
    val brand: TvBrand
    val capabilities: Set<TransportCapability>
    suspend fun discover(): List<RemoteTarget>
    suspend fun connect(target: RemoteTarget): Result<Unit>
    suspend fun send(key: RemoteKey): Result<Unit>
    suspend fun sendText(text: String): Result<Unit>
    suspend fun wakeOnLan(mac: String): Result<Unit>
}
