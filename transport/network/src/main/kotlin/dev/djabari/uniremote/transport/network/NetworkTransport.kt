package dev.djabari.uniremote.transport.network

import dev.djabari.uniremote.model.KeyAction
import dev.djabari.uniremote.model.PointerEvent
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TransportId
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.transport.RemoteTransport
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportError
import dev.djabari.uniremote.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fallback transport for TVs that reject the BT HID role (e.g. Roku) and for cold power-on,
 * which Bluetooth physically cannot do.
 *
 * One [BrandAdapter] per ecosystem behind a single transport, so the rest of the app
 * sees a consistent [RemoteTransport] interface.
 */
class NetworkTransport(
    private val adapters: List<BrandAdapter>,
) : RemoteTransport {

    override val id = TransportId.NETWORK

    private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<TransportCapability>>(emptySet())
    override val capabilities: StateFlow<Set<TransportCapability>> = _capabilities.asStateFlow()

    private var activeAdapter: BrandAdapter? = null
    private var currentTarget: RemoteTarget? = null

    override fun supports(target: RemoteTarget): Boolean =
        (target.ipAddress != null || target.macAddress != null) &&
            adapters.any { it.brand == target.brand || target.brand == TvBrand.GENERIC }

    override suspend fun connect(target: RemoteTarget): Result<Unit> {
        val adapter = adapters.firstOrNull { it.brand == target.brand }
            ?: adapters.firstOrNull { target.brand == TvBrand.GENERIC }
            ?: run {
                _state.value = TransportState.Failed(TransportError.UNSUPPORTED_TARGET)
                return Result.failure(IllegalArgumentException("No network adapter for brand: ${target.brand}"))
            }

        currentTarget = target
        _state.value = TransportState.Connecting(target)

        val connectResult = adapter.connect(target)
        return if (connectResult.isSuccess) {
            activeAdapter = adapter
            _state.value = TransportState.Connected(target)
            _capabilities.value = adapter.capabilities
            Result.success(Unit)
        } else {
            _state.value = TransportState.Failed(
                TransportError.TARGET_UNREACHABLE,
                connectResult.exceptionOrNull(),
            )
            connectResult
        }
    }

    override suspend fun disconnect() {
        activeAdapter?.disconnect()
        activeAdapter = null
        currentTarget = null
        _capabilities.value = emptySet()
        _state.value = TransportState.Idle
    }

    override suspend fun sendKey(key: RemoteKey, action: KeyAction): Result<Unit> {
        val adapter = activeAdapter
            ?: return Result.failure(IllegalStateException("No connected network adapter"))

        // Special handling for Power On via Wake-on-LAN
        if (key == RemoteKey.POWER_ON) {
            val mac = currentTarget?.macAddress
            return if (mac != null) {
                adapter.wakeOnLan(mac)
            } else {
                Result.failure(IllegalArgumentException("POWER_ON requires a MAC address for Wake-on-LAN"))
            }
        }

        return adapter.send(key)
    }

    override suspend fun sendText(text: String): Result<Unit> {
        val adapter = activeAdapter
            ?: return Result.failure(IllegalStateException("No connected network adapter"))
        return adapter.sendText(text)
    }

    override suspend fun sendPointer(event: PointerEvent): Result<Unit> {
        // Network transports typically do not offer low-latency pointer control
        return Result.failure(UnsupportedOperationException("Pointer control is not supported over network"))
    }

}

/**
 * Per-ecosystem protocol adapter.
 */
interface BrandAdapter {
    val brand: TvBrand
    val capabilities: Set<TransportCapability>
    suspend fun discover(): List<RemoteTarget>
    suspend fun connect(target: RemoteTarget): Result<Unit>
    suspend fun send(key: RemoteKey): Result<Unit>
    suspend fun sendText(text: String): Result<Unit>
    suspend fun wakeOnLan(mac: String): Result<Unit>
    suspend fun disconnect()
}
