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
 *
 * An unlabelled target ([TvBrand.GENERIC] / [TvBrand.UNKNOWN]) is never blindly handed to
 * the first adapter: Samsung and LG connects open a pairing prompt on the TV screen, so a
 * blind probe would spam prompts across every TV in the house. Unlabelled targets go
 * through [BrandAdapter.identify] — silent and side-effect free — and only the adapter
 * that answers it gets the connection. Failures surface as [TransportError.UNSUPPORTED_TARGET]
 * so the caller can ask the user to pick a brand instead of guessing.
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

    /** Brand identified for the last unlabelled connect, exposed for persistence. */
    private var resolved: RemoteTarget? = null
    override val resolvedTarget: RemoteTarget? get() = resolved

    override fun supports(target: RemoteTarget): Boolean {
        if (target.ipAddress == null && target.macAddress == null) return false
        return if (target.brand.isUnlabelled) {
            // Only a silent, IP-based identification can route an unlabelled TV.
            target.ipAddress != null
        } else {
            adapters.any { it.brand == target.brand }
        }
    }

    override suspend fun connect(target: RemoteTarget): Result<Unit> {
        resolved = null
        currentTarget = target
        _state.value = TransportState.Connecting(target)

        val adapter = resolveAdapter(target) ?: run {
            _state.value = TransportState.Failed(TransportError.UNSUPPORTED_TARGET)
            return Result.failure(
                IllegalStateException(
                    if (target.brand.isUnlabelled) {
                        "Couldn't identify this TV from its IP. Use \"Add TV by IP / Brand\" and pick its brand."
                    } else {
                        "No network adapter for brand: ${target.brand}"
                    },
                ),
            )
        }

        val connectResult = adapter.connect(target)
        return if (connectResult.isSuccess) {
            activeAdapter = adapter
            if (target.brand.isUnlabelled) resolved = target.copy(brand = adapter.brand)
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

    /**
     * The adapter for a labelled target is its brand match. An unlabelled target is probed
     * silently — [BrandAdapter.identify] must never open anything that prompts on the TV —
     * and the first adapter that answers owns the connection.
     */
    private suspend fun resolveAdapter(target: RemoteTarget): BrandAdapter? {
        if (target.brand.isUnlabelled) {
            if (target.ipAddress == null) return null
            return adapters.firstOrNull { it.identify(target) }
        }
        return adapters.firstOrNull { it.brand == target.brand }
    }

    override suspend fun disconnect() {
        activeAdapter?.disconnect()
        activeAdapter = null
        currentTarget = null
        resolved = null
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

    /**
     * Cheap, silent check for whether [target] is this ecosystem. Must not open anything
     * that prompts on the TV screen — used to route unlabelled targets. Defaults to false:
     * only ecosystems with an unauthenticated handshake (Roku ECP) can answer one of these.
     */
    suspend fun identify(target: RemoteTarget): Boolean = false

    suspend fun discover(): List<RemoteTarget>
    suspend fun connect(target: RemoteTarget): Result<Unit>
    suspend fun send(key: RemoteKey): Result<Unit>
    suspend fun sendText(text: String): Result<Unit>
    suspend fun wakeOnLan(mac: String): Result<Unit>
    suspend fun disconnect()
}

/** A TV whose ecosystem is not yet known cannot be routed without a silent probe. */
private val TvBrand.isUnlabelled: Boolean
    get() = this == TvBrand.GENERIC || this == TvBrand.UNKNOWN
