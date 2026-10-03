package dev.djabari.uniremote.session

import dev.djabari.uniremote.model.KeyAction
import dev.djabari.uniremote.model.PointerEvent
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.transport.RemoteTransport
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportError
import dev.djabari.uniremote.transport.TransportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRemoteSession @Inject constructor(
    private val selector: TransportSelector,
    private val repository: TargetStore,
    private val hidProfileController: HidProfileController,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : RemoteSession {

    private val _activeTarget = MutableStateFlow<RemoteTarget?>(null)
    override val activeTarget: StateFlow<RemoteTarget?> = _activeTarget.asStateFlow()

    private val _activeTransport = MutableStateFlow<RemoteTransport?>(null)

    /**
     * Serialises connection attempts. `onResume` fires a reconnect, and a permission dialog
     * or a rotation can fire several in quick succession — without this they would race and
     * each register a Bluetooth HID app.
     */
    private val connectionLock = Mutex()

    /** The connect currently in flight, so an explicit disconnect can cut it short. */
    private var attempt: Deferred<Result<Unit>>? = null

    /**
     * Set when the user disconnects on purpose. Without it the `onResume` reconnect would
     * override that decision the moment they come back to the app.
     */
    private var disconnectedByUser = false

    private var stateObservationJob: Job? = null
    private var capsObservationJob: Job? = null
    @Volatile
    private var recoveryJob: Job? = null

    private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<TransportCapability>>(emptySet())
    override val capabilities: StateFlow<Set<TransportCapability>> = _capabilities.asStateFlow()

    init {
        scope.launch {
            _activeTransport.collect { transport ->
                stateObservationJob?.cancel()
                capsObservationJob?.cancel()

                if (transport != null) {
                    stateObservationJob = launch {
                        transport.state.collect { state ->
                            if (state is TransportState.Failed && state.reason == TransportError.CONNECTION_LOST) {
                                _state.value = state
                                scheduleRecovery(transport)
                            } else if (recoveryJob?.isActive != true) {
                                _state.value = state
                            }
                        }
                    }
                    capsObservationJob = launch {
                        transport.capabilities.collect { _capabilities.value = it }
                    }
                } else {
                    _capabilities.value = emptySet()
                }
            }
        }
    }

    override suspend fun connect(target: RemoteTarget): Result<Unit> {
        recoveryJob?.cancel()
        recoveryJob = null
        disconnectedByUser = false
        // Runs in the session scope, not the caller's: a connect must survive the screen
        // that started it going away, and must stay cancellable by [disconnect].
        val running = scope.async { connectionLock.withLock { connectLocked(target) } }
        attempt = running

        return try {
            running.await()
        } catch (_: CancellationException) {
            Result.failure(IllegalStateException("Connection attempt was cancelled"))
        } finally {
            if (attempt === running) attempt = null
        }
    }

    private fun scheduleRecovery(transport: RemoteTransport) {
        val target = _activeTarget.value ?: return
        if (disconnectedByUser || attempt?.isActive == true || connectionLock.isLocked || recoveryJob?.isActive == true || _activeTransport.value !== transport) return
        recoveryJob = scope.launch {
            for (attemptNumber in 1..AUTO_RECONNECT_ATTEMPTS) {
                _state.value = TransportState.Reconnecting(target, attemptNumber, AUTO_RECONNECT_ATTEMPTS)
                delay(AUTO_RECONNECT_DELAYS_MS[attemptNumber - 1])
                val latestFailure = transport.state.value as? TransportState.Failed
                if (latestFailure != null && latestFailure.reason in NON_RETRYABLE_ERRORS) {
                    _state.value = latestFailure
                    return@launch
                }
                val result = connectionLock.withLock { connectLocked(target) }
                if (result.isSuccess) {
                    _state.value = _activeTransport.value?.state?.value ?: transport.state.value
                    return@launch
                }
                val finalState = _state.value as? TransportState.Failed
                if (finalState?.reason in NON_RETRYABLE_ERRORS) return@launch
            }
            // connectLocked owns the final failure state; the mirror stays suppressed while this job runs.
        }
    }

    private suspend fun connectLocked(target: RemoteTarget): Result<Unit> {
        _activeTarget.value = target
        val candidates = selector.candidatesFor(target)
        if (candidates.isEmpty()) {
            _state.value = TransportState.Failed(TransportError.UNSUPPORTED_TARGET)
            return Result.failure(IllegalArgumentException("No compatible transport found for ${target.brand}"))
        }

        var lastError: Throwable? = null
        var failureReason: TransportError? = null
        for (transport in candidates) {
            _activeTransport.value = transport
            val result = transport.connect(target)
            if (result.isSuccess) {
                // A transport may have resolved the target better than we knew it (e.g. the
                // network transport silently identified the brand of an unlabelled TV).
                // Persist that so the identification never has to run again.
                repository.saveTarget(transport.resolvedTarget ?: target)
                repository.setLastConnectedTargetId(target.id)
                return Result.success(Unit)
            } else {
                lastError = result.exceptionOrNull()
                // The first candidate is the highest-priority transport, so its reason is the
                // most useful diagnostic (e.g. BT HID registration held by another app).
                if (failureReason == null) {
                    failureReason = (transport.state.value as? TransportState.Failed)?.reason
                }
            }
        }

        _activeTransport.value = null
        val finalError = lastError ?: IllegalStateException("Connection failed on all candidates")
        _state.value = TransportState.Failed(failureReason ?: TransportError.TARGET_UNREACHABLE, finalError)
        return Result.failure(finalError)
    }

    /**
     * Re-establishes the last session. Called when the app returns to the foreground: TVs
     * drop idle HID links and users will not tolerate re-pairing to get the remote back.
     */
    override suspend fun reconnect(): Result<Unit> = connectionLock.withLock {
        // Ask the transport itself rather than the mirrored state, which lags by one
        // dispatch and would otherwise cause a redundant reconnect right after connecting.
        // Checked inside the lock so a queued reconnect turns into a no-op once the
        // attempt ahead of it has succeeded.
        if (_activeTransport.value?.state?.value is TransportState.Connected) {
            return@withLock Result.success(Unit)
        }
        if (disconnectedByUser) {
            return@withLock Result.failure(IllegalStateException("Disconnected by the user"))
        }

        val target = _activeTarget.value ?: lastSavedTarget()
            ?: return@withLock Result.failure(IllegalStateException("No target to reconnect to"))
        connectLocked(target)
    }

    override suspend fun resetConnection(): Result<Unit> {
        recoveryJob?.cancel()
        recoveryJob = null
        disconnectedByUser = false
        return connectionLock.withLock {
            val target = _activeTarget.value ?: lastSavedTarget()
                ?: return@withLock Result.failure(IllegalStateException("No target to reconnect to"))
            _activeTransport.value?.disconnect()
            hidProfileController.reopen()
            connectLocked(target)
        }
    }

    private suspend fun lastSavedTarget(): RemoteTarget? {
        val lastId = repository.lastConnectedTargetId.first() ?: return null
        return repository.savedTargets.first().firstOrNull { it.id == lastId }
    }

    override suspend fun disconnect() {
        recoveryJob?.cancel()
        recoveryJob = null
        disconnectedByUser = true
        // Cut off any attempt still waiting on a TV so the user is not stuck behind a
        // 15-second Bluetooth timeout after asking to disconnect.
        attempt?.cancel()

        connectionLock.withLock { disconnectLocked() }
    }

    private suspend fun disconnectLocked() {
        _activeTransport.value?.disconnect()
        _activeTransport.value = null
        _activeTarget.value = null
        _state.value = TransportState.Idle
        _capabilities.value = emptySet()
    }

    override suspend fun press(key: RemoteKey, action: KeyAction): Result<Unit> {
        val transport = _activeTransport.value
            ?: return Result.failure(IllegalStateException("No active transport connected"))
        return transport.sendKey(key, action)
    }

    override suspend fun type(text: String): Result<Unit> {
        val transport = _activeTransport.value
            ?: return Result.failure(IllegalStateException("No active transport connected"))
        return transport.sendText(text)
    }

    override suspend fun pointer(event: PointerEvent): Result<Unit> {
        val transport = _activeTransport.value
            ?: return Result.failure(IllegalStateException("No active transport connected"))
        return transport.sendPointer(event)
    }

    companion object {
        private const val AUTO_RECONNECT_ATTEMPTS = 3
        private val AUTO_RECONNECT_DELAYS_MS = longArrayOf(2_000, 5_000, 10_000)
        private val NON_RETRYABLE_ERRORS = setOf(
            TransportError.BLUETOOTH_DISABLED,
            TransportError.NOT_PAIRED,
            TransportError.PERMISSION_DENIED,
            TransportError.UNSUPPORTED_TARGET,
        )
    }
}
