package dev.djabari.uniremote.session

import dev.djabari.uniremote.model.*
import dev.djabari.uniremote.transport.*
import kotlinx.coroutines.flow.*

/**
 * The single surface the UI talks to. Features never import a transport implementation.
 */
interface RemoteSession {
    val state: StateFlow<TransportState>
    val capabilities: StateFlow<Set<TransportCapability>>
    val activeTarget: StateFlow<RemoteTarget?>

    suspend fun connect(target: RemoteTarget): Result<Unit>

    /** Re-establishes the last session after an idle drop or an app resume. */
    suspend fun reconnect(): Result<Unit>

    /**
     * Tears the Bluetooth stack state down and connects to the last target from scratch —
     * the in-app equivalent of force-stopping the app, for links a flaky TV left wedged.
     */
    suspend fun resetConnection(): Result<Unit>

    suspend fun disconnect()
    suspend fun press(key: RemoteKey, action: KeyAction = KeyAction.TAP): Result<Unit>
    suspend fun type(text: String): Result<Unit>
    suspend fun pointer(event: PointerEvent): Result<Unit>
}

/** Picks the transports that can reach a target, in the order they are tried. */
class TransportSelector(
    private val transports: List<RemoteTransport>,
) {
    fun candidatesFor(target: RemoteTarget): List<RemoteTransport> =
        transports.filter { it.supports(target) }
}
