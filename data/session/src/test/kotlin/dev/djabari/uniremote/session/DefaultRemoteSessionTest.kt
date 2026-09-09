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
import dev.djabari.uniremote.transport.TransportState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRemoteSessionTest {

    private class FakeTransport(
        override val id: TransportId,
        private val connectResult: Result<Unit>,
        private val transportCapabilities: Set<TransportCapability> = emptySet(),
        private val connectDelayMs: Long = 0,
        private val resolved: RemoteTarget? = null,
    ) : RemoteTransport {
        var connectCount = 0
        var disconnectCount = 0
        val sentKeys = mutableListOf<RemoteKey>()

        private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
        override val state: StateFlow<TransportState> = _state
        private val _capabilities = MutableStateFlow<Set<TransportCapability>>(emptySet())
        override val capabilities: StateFlow<Set<TransportCapability>> = _capabilities
        override val resolvedTarget: RemoteTarget? get() = resolved

        override fun supports(target: RemoteTarget) = true

        override suspend fun connect(target: RemoteTarget): Result<Unit> {
            connectCount++
            if (connectDelayMs > 0) delay(connectDelayMs)
            if (connectResult.isSuccess) {
                _state.value = TransportState.Connected(target)
                _capabilities.value = transportCapabilities
            } else {
                _state.value = TransportState.Idle
            }
            return connectResult
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
        val session = DefaultRemoteSession(TransportSelector(listOf(bt, network)), store, backgroundScope)

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
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), store, backgroundScope)

        val result = session.connect(generic)

        assertThat(result.isSuccess).isTrue()
        assertThat(store.saved).containsExactly(resolved)
    }

    @Test
    fun `reports failure when no transport can connect`() = runTest {
        val bt = FakeTransport(TransportId.BLUETOOTH_HID, Result.failure(IllegalStateException("refused")))
        val network = FakeTransport(TransportId.NETWORK, Result.failure(IllegalStateException("unreachable")))
        val session = DefaultRemoteSession(TransportSelector(listOf(bt, network)), FakeTargetStore(), backgroundScope)

        val result = session.connect(target)

        assertThat(result.isFailure).isTrue()
        assertThat(session.state.value).isInstanceOf(TransportState.Failed::class.java)
    }

    @Test
    fun `keys go to the transport that actually connected`() = runTest {
        val bt = FakeTransport(TransportId.BLUETOOTH_HID, Result.failure(IllegalStateException("refused")))
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val session = DefaultRemoteSession(TransportSelector(listOf(bt, network)), FakeTargetStore(), backgroundScope)
        session.connect(target)

        session.press(RemoteKey.VOLUME_UP)

        assertThat(network.sentKeys).containsExactly(RemoteKey.VOLUME_UP)
        assertThat(bt.sentKeys).isEmpty()
    }

    @Test
    fun `reconnect restores the last saved target after a cold start`() = runTest {
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val store = FakeTargetStore(targets = listOf(target), lastId = target.id)
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), store, backgroundScope)

        val result = session.reconnect()

        assertThat(result.isSuccess).isTrue()
        assertThat(network.connectCount).isEqualTo(1)
        assertThat(session.activeTarget.value).isEqualTo(target)
    }

    @Test
    fun `overlapping reconnects produce a single connection attempt`() = runTest {
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val store = FakeTargetStore(targets = listOf(target), lastId = target.id)
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), store, backgroundScope)

        val attempts = List(5) { async { session.reconnect() } }
        attempts.awaitAll()

        assertThat(network.connectCount).isEqualTo(1)
    }

    @Test
    fun `an explicit disconnect is not undone by the next resume`() = runTest {
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val store = FakeTargetStore(targets = listOf(target), lastId = target.id)
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), store, backgroundScope)
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
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), store, backgroundScope)
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
        val session = DefaultRemoteSession(TransportSelector(listOf(slow)), FakeTargetStore(), backgroundScope)
        val connecting = async { session.connect(target) }
        runCurrent()

        session.disconnect()

        assertThat(connecting.await().isFailure).isTrue()
        assertThat(testScheduler.currentTime).isLessThan(15_000L)
    }

    @Test
    fun `reconnect is a no-op while already connected`() = runTest {
        val network = FakeTransport(TransportId.NETWORK, Result.success(Unit))
        val session = DefaultRemoteSession(TransportSelector(listOf(network)), FakeTargetStore(), backgroundScope)
        session.connect(target)

        session.reconnect()

        assertThat(network.connectCount).isEqualTo(1)
    }
}
