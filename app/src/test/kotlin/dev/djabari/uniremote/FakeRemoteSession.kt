package dev.djabari.uniremote

import dev.djabari.uniremote.model.KeyAction
import dev.djabari.uniremote.model.PointerEvent
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.session.RemoteSession
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Deterministic session for screenshot tests: state, capabilities and target are driven
 * directly instead of through a real transport. Feature ViewModels are constructed against
 * this — no Hilt in tests.
 */
class FakeRemoteSession : RemoteSession {

    private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = _state

    private val _capabilities = MutableStateFlow<Set<TransportCapability>>(emptySet())
    override val capabilities: StateFlow<Set<TransportCapability>> = _capabilities

    private val _activeTarget = MutableStateFlow<RemoteTarget?>(null)
    override val activeTarget: StateFlow<RemoteTarget?> = _activeTarget

    fun setConnected(target: RemoteTarget, caps: Set<TransportCapability>) {
        _activeTarget.value = target
        _capabilities.value = caps
        _state.value = TransportState.Connected(target)
    }

    fun setDisconnected() {
        _activeTarget.value = null
        _capabilities.value = emptySet()
        _state.value = TransportState.Idle
    }

    override suspend fun connect(target: RemoteTarget): Result<Unit> = Result.success(Unit)
    override suspend fun reconnect(): Result<Unit> = Result.success(Unit)
    override suspend fun resetConnection(): Result<Unit> = Result.success(Unit)
    override suspend fun disconnect() = Unit
    override suspend fun press(key: RemoteKey, action: KeyAction): Result<Unit> = Result.success(Unit)
    override suspend fun type(text: String): Result<Unit> = Result.success(Unit)
    override suspend fun pointer(event: PointerEvent): Result<Unit> = Result.success(Unit)
}
