package dev.djabari.tvremote.session

import dev.djabari.tvremote.model.*
import dev.djabari.tvremote.transport.*
import kotlinx.coroutines.flow.*

/**
 * The single surface the UI talks to. Features never import a transport implementation —
 * that is what keeps Phase 4 from rippling into every screen.
 */
interface RemoteSession {
    val state: StateFlow<TransportState>
    val capabilities: StateFlow<Set<TransportCapability>>
    val activeTarget: StateFlow<RemoteTarget?>

    suspend fun connect(target: RemoteTarget): Result<Unit>
    suspend fun disconnect()
    suspend fun press(key: RemoteKey, action: KeyAction = KeyAction.TAP): Result<Unit>
    suspend fun type(text: String): Result<Unit>
    suspend fun pointer(event: PointerEvent): Result<Unit>
}

/**
 * Picks a transport for a target and falls back when the preferred one fails.
 *
 * Order: explicit user preference -> BT HID (if the brand accepts it) -> network.
 * A [TransportError.HID_REGISTRATION_FAILED] is the signal to demote BT HID for this
 * device permanently, not to retry.
 */
class TransportSelector(
    private val transports: List<RemoteTransport>,
) {
    fun candidatesFor(target: RemoteTarget): List<RemoteTransport> = buildList {
        target.preferredTransport
            ?.let { pref -> transports.firstOrNull { it.id == pref } }
            ?.let(::add)
        transports
            .filter { it.supports(target) && it !in this }
            .sortedBy { if (it.id == TransportId.BLUETOOTH_HID) 0 else 1 }
            .let(::addAll)
    }
}
