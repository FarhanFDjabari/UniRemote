package dev.djabari.uniremote.session

import com.google.common.truth.Truth.assertThat
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRemoteSessionTest {

    private class FakeTransport(
        override val id: TransportId,
        private val connectResult: Result<Unit>,
        private val transportCapabilities: Set<TransportCapability> = emptySet(),
        var connectDelayMs: Long = 0,
        private val resolved: RemoteTarget? = null,
        private val failureReason: TransportError? = null,
    ) : RemoteTransport {
        val connectResults = ArrayDeque<Result<Unit>>().apply { add(connectResult) }
        var connectCount = 0
        var disconnectCount = 0
        val sentKeys = mutableListOf<RemoteKey>()

        private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
        override val state: StateFlow<TransportState> = _state
        private val _capabilities = MutableStateFlow<Set<TransportCapability>>(emptySet())
        override val capabilities: StateFlow<Set<TransportCapability>> = _capabilities
        override val resolvedTarget: RemoteTarget? get() = resolved
        fun pushState(state: TransportState) { _state.value = state }

        override fun supports(target: RemoteTarget) = true

        override suspend fun connect(target: RemoteTarget): Result<Unit> {
            connectCount++
            if (connectDelayMs > 0) delay(connectDelayMs)
            val result = connectResults.removeFirstOrNull() ?: connectResult
            if (result.isSuccess) {
                _state.value = TransportState.Connected(target)
                _capabilities.value = transportCapabilities
            } else {
                _state.value = failureReason?.let { TransportState.Failed(it) } ?: TransportState.Idle
            }
            return result
        }

        override suspend fun disconnect() {
            disconnectCount++
            _state.value = TransportState.Idle
            _capabilities.value = emptySet()
        }

        override suspend fun sendKey(key: RemoteKey, action: KeyAction): Result<Unit> {
            sentKeys += key
            return Result.success(Unit)
        }

        override suspend fun sendText(text: String) = Result.success(Unit)
        override suspend fun sendPointer(event: PointerEvent) = Result.success(Unit)
    }

    private class FakeProfileController : HidProfileController {
        var reopenCount = 0
        override fun open() = Unit
        override fun close() = Unit
        override suspend fun reopen() { reopenCount++ }
    }

    private class FakeTargetStore(
        targets: List<RemoteTarget> = emptyList(),
        lastId: String? = null,
    ) : TargetStore {
        val saved = targets.toMutableList()
        var lastConnectedId: String? = lastId

        override val savedTargets: Flow<List<RemoteTarget>> get() = flowOf(saved.toList())
        override val lastConnectedTargetId: Flow<String?> get() = flowOf(lastConnectedId)

        override suspend fun saveTarget(target: RemoteTarget) {
            saved.removeAll { it.id == target.id }
            saved += target
        }

        override suspend fun setLastConnectedTargetId(targetId: String) {
            lastConnectedId = targetId
        }
    }

    private val target = RemoteTarget(
        id = "tv-1",
        displayName = "Living room",
        brand = TvBrand.SAMSUNG_TIZEN,
        bluetoothAddress = "AA:BB:CC:DD:EE:FF",
        ipAddress = "192.168.1.9",
    )

    @Test
    fun `falls back to the network transport when Bluetooth HID cannot connect`() = runTest {
        val bt = FakeTransport(TransportId.BLUETOOTH_HID, Result.failure(IllegalStateException("refused")))
        val network = FakeTransport(
            TransportId.NETWORK,
            Result.success(Unit),
            setOf(TransportCapability.DPAD),
        )
        val store = FakeTargetStore()
        val session = DefaultRemoteSession(TransportSelector(listOf(bt, network)), store, FakeProfileController(), backgroundScope)

        val result = session.connect(target)

        assertThat(result.isSuccess).isTrue()
        assertThat(bt.connectCount).isEqualTo(1)
        assertThat(network.connectCount).isEqualTo(1)
        assertThat(store.saved).containsExactly(target)
        assertThat(store.lastConnectedId).isEqualTo("tv-1")
    }

    @Test
    fun `persists the brand a transport resolved for an unlabelled target`() = runTest {
        val generic = target.copy(brand = TvBrand.GENERIC)
        val resolved = generic.copy(brand = TvBrand.ROKU)
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit), resolved = resolved)
        val store = FakeTargetStore()
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), store, FakeProfileController(), backgroundScope)

        val result = session.connect(generic)

        assertThat(result.isSuccess).isTrue()
        assertThat(store.saved).containsExactly(resolved)
    }

    @Test
    fun `reports failure when no transport can connect`() = runTest {
        val bt = FakeTransport(TransportId.BLUETOOTH_HID, Result.failure(IllegalStateException("refused")))
        val network = FakeTransport(TransportId.NETWORK, Result.failure(IllegalStateException("unreachable")))
        val session = DefaultRemoteSession(TransportSelector(listOf(bt, network)), FakeTargetStore(), FakeProfileController(), backgroundScope)

        val result = session.connect(target)
        runCurrent()

        assertThat(result.isFailure).isTrue()
        assertThat(session.state.value).isInstanceOf(TransportState.Failed::class.java)
    }

    @Test
    fun `surfaces the preferred transport's failure reason when every transport fails`() = runTest {
        val bt = FakeTransport(
            TransportId.BLUETOOTH_HID,
            Result.failure(IllegalStateException("another app holds the HID registration")),
            failureReason = TransportError.HID_REGISTRATION_FAILED,
        )
        val network = FakeTransport(
            TransportId.NETWORK,
            Result.failure(IllegalStateException("unreachable")),
            failureReason = TransportError.TARGET_UNREACHABLE,
        )
        val session = DefaultRemoteSession(TransportSelector(listOf(bt, network)), FakeTargetStore(), FakeProfileController(), backgroundScope)

        session.connect(target)

        val failed = session.state.value as TransportState.Failed
        assertThat(failed.reason).isEqualTo(TransportError.HID_REGISTRATION_FAILED)
    }

    @Test
    fun `falls back to TARGET_UNREACHABLE when no transport reports a reason`() = runTest {
        val bt = FakeTransport(TransportId.BLUETOOTH_HID, Result.failure(IllegalStateException("refused")))
        val network = FakeTransport(TransportId.NETWORK, Result.failure(IllegalStateException("unreachable")))
        val session = DefaultRemoteSession(TransportSelector(listOf(bt, network)), FakeTargetStore(), FakeProfileController(), backgroundScope)

        session.connect(target)

        val failed = session.state.value as TransportState.Failed
        assertThat(failed.reason).isEqualTo(TransportError.TARGET_UNREACHABLE)
    }

    @Test
    fun `keys go to the transport that actually connected`() = runTest {
        val bt = FakeTransport(TransportId.BLUETOOTH_HID, Result.failure(IllegalStateException("refused")))
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val session = DefaultRemoteSession(TransportSelector(listOf(bt, network)), FakeTargetStore(), FakeProfileController(), backgroundScope)
        session.connect(target)

        session.press(RemoteKey.VOLUME_UP)

        assertThat(network.sentKeys).containsExactly(RemoteKey.VOLUME_UP)
        assertThat(bt.sentKeys).isEmpty()
    }

    @Test
    fun `reconnect restores the last saved target after a cold start`() = runTest {
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val store = FakeTargetStore(targets = listOf(target), lastId = target.id)
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), store, FakeProfileController(), backgroundScope)

        val result = session.reconnect()

        assertThat(result.isSuccess).isTrue()
        assertThat(network.connectCount).isEqualTo(1)
        assertThat(session.activeTarget.value).isEqualTo(target)
    }

    @Test
    fun `overlapping reconnects produce a single connection attempt`() = runTest {
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val store = FakeTargetStore(targets = listOf(target), lastId = target.id)
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), store, FakeProfileController(), backgroundScope)

        val attempts = List(5) { async { session.reconnect() } }
        attempts.awaitAll()

        assertThat(network.connectCount).isEqualTo(1)
    }

    @Test
    fun `an explicit disconnect is not undone by the next resume`() = runTest {
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val store = FakeTargetStore(targets = listOf(target), lastId = target.id)
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), store, FakeProfileController(), backgroundScope)
        session.connect(target)

        session.disconnect()
        val result = session.reconnect()

        assertThat(result.isFailure).isTrue()
        assertThat(network.connectCount).isEqualTo(1)
    }

    @Test
    fun `connecting again after an explicit disconnect re-arms reconnect`() = runTest {
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val store = FakeTargetStore(targets = listOf(target), lastId = target.id)
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), store, FakeProfileController(), backgroundScope)
        session.connect(target)
        session.disconnect()

        session.connect(target)
        val result = session.reconnect()

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `disconnect does not wait out a connect attempt that is stuck on a TV`() = runTest {
        val slow = FakeTransport(
            TransportId.BLUETOOTH_HID,
            Result.success(Unit),
            connectDelayMs = 15_000,
        )
        val session = DefaultRemoteSession(TransportSelector(listOf(slow)), FakeTargetStore(), FakeProfileController(), backgroundScope)
        val connecting = async { session.connect(target) }
        runCurrent()

        session.disconnect()

        assertThat(connecting.await().isFailure).isTrue()
        assertThat(testScheduler.currentTime).isLessThan(15_000L)
    }

    @Test
    fun `reconnect is a no-op while already connected`() = runTest {
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), FakeTargetStore(), FakeProfileController(), backgroundScope)
        session.connect(target)

        session.reconnect()

        assertThat(network.connectCount).isEqualTo(1)
    }

    @Test
    fun `connection loss reconnects after the first backoff`() = runTest {
        val transport = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val session = DefaultRemoteSession(TransportSelector(listOf(transport)), FakeTargetStore(), FakeProfileController(), backgroundScope)
        session.connect(target)
        transport.pushState(TransportState.Failed(TransportError.CONNECTION_LOST))
        runCurrent()
        assertThat(session.state.value).isEqualTo(TransportState.Reconnecting(target, 1, 3))

        advanceTimeBy(2_000)
        runCurrent()

        assertThat(transport.connectCount).isEqualTo(2)
        assertThat(session.state.value).isEqualTo(TransportState.Connected(target))
    }

    @Test
    fun `recovery gives up after three failed attempts`() = runTest {
        val transport = FakeTransport(TransportId.NETWORK, Result.success(Unit), failureReason = TransportError.CONNECTION_LOST)
        val session = DefaultRemoteSession(TransportSelector(listOf(transport)), FakeTargetStore(), FakeProfileController(), backgroundScope)
        session.connect(target)
        transport.connectResults.clear()
        repeat(3) { transport.connectResults.add(Result.failure(IllegalStateException("lost"))) }
        transport.pushState(TransportState.Failed(TransportError.CONNECTION_LOST))
        runCurrent()
        advanceTimeBy(17_000)
        runCurrent()

        assertThat(transport.connectCount).isEqualTo(4)
        assertThat(session.state.value).isInstanceOf(TransportState.Failed::class.java)
    }

    @Test
    fun `disconnect cancels recovery during backoff`() = runTest {
        val transport = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val session = DefaultRemoteSession(TransportSelector(listOf(transport)), FakeTargetStore(), FakeProfileController(), backgroundScope)
        session.connect(target)
        transport.pushState(TransportState.Failed(TransportError.CONNECTION_LOST))
        runCurrent()
        session.disconnect()
        advanceTimeBy(20_000)
        runCurrent()

        assertThat(transport.connectCount).isEqualTo(1)
    }

    @Test
    fun `reset reopens profile and reconnects saved target after user disconnect`() = runTest {
        val transport = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val profile = FakeProfileController()
        val store = FakeTargetStore(listOf(target), target.id)
        val session = DefaultRemoteSession(TransportSelector(listOf(transport)), store, profile, backgroundScope)
        session.connect(target)
        session.disconnect()

        val result = session.resetConnection()

        assertThat(result.isSuccess).isTrue()
        assertThat(profile.reopenCount).isEqualTo(1)
        assertThat(transport.disconnectCount).isEqualTo(1)
        assertThat(transport.connectCount).isEqualTo(2)
    }

    @Test
    fun `reset disconnects the active transport before reopening`() = runTest {
        val transport = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val profile = FakeProfileController()
        val session = DefaultRemoteSession(TransportSelector(listOf(transport)), FakeTargetStore(listOf(target), target.id), profile, backgroundScope)
        session.connect(target)

        val result = session.resetConnection()

        assertThat(result.isSuccess).isTrue()
        assertThat(transport.disconnectCount).isEqualTo(1)
        assertThat(profile.reopenCount).isEqualTo(1)
        assertThat(transport.connectCount).isEqualTo(2)
    }

    @Test
    fun `reset without saved target fails`() = runTest {
        val transport = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val session = DefaultRemoteSession(TransportSelector(listOf(transport)), FakeTargetStore(), FakeProfileController(), backgroundScope)

        assertThat(session.resetConnection().exceptionOrNull()?.message).isEqualTo("No target to reconnect to")
    }

    @Test
    fun `user disconnect prevents automatic recovery`() = runTest {
        val transport = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val session = DefaultRemoteSession(TransportSelector(listOf(transport)), FakeTargetStore(listOf(target), target.id), FakeProfileController(), backgroundScope)
        session.connect(target)
        session.disconnect()
        transport.pushState(TransportState.Failed(TransportError.CONNECTION_LOST))
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()

        assertThat(transport.connectCount).isEqualTo(1)
    }

    @Test
    fun `non retryable failure stops recovery`() = runTest {
        val transport = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val session = DefaultRemoteSession(TransportSelector(listOf(transport)), FakeTargetStore(), FakeProfileController(), backgroundScope)
        session.connect(target)
        transport.pushState(TransportState.Failed(TransportError.CONNECTION_LOST))
        runCurrent()
        transport.pushState(TransportState.Failed(TransportError.NOT_PAIRED))
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()

        assertThat(transport.connectCount).isEqualTo(1)
        assertThat(session.state.value).isEqualTo(TransportState.Failed(TransportError.NOT_PAIRED))
    }

    @Test
    fun `link loss stays failed when reconnect already holds the connection lock`() = runTest {
        val transport = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val session = DefaultRemoteSession(TransportSelector(listOf(transport)), FakeTargetStore(), FakeProfileController(), backgroundScope)
        session.connect(target)
        transport.connectDelayMs = 5_000
        transport.pushState(TransportState.Idle)
        val reconnect = async { session.reconnect() }
        runCurrent()

        transport.pushState(TransportState.Failed(TransportError.CONNECTION_LOST))
        runCurrent()

        assertThat(session.state.value).isEqualTo(TransportState.Failed(TransportError.CONNECTION_LOST))
        reconnect.cancelAndJoin()
    }
}
