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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

class TransportSelectorTest {

    private class TestTransport(
        override val id: TransportId,
        private val supportedBrands: Set<TvBrand>,
    ) : RemoteTransport {
        override val state: StateFlow<TransportState> = MutableStateFlow(TransportState.Idle)
        override val capabilities: StateFlow<Set<TransportCapability>> = MutableStateFlow(emptySet())
        override fun supports(target: RemoteTarget): Boolean = supportedBrands.contains(target.brand)
        override suspend fun connect(target: RemoteTarget): Result<Unit> = Result.success(Unit)
        override suspend fun disconnect() {}
        override suspend fun sendKey(key: RemoteKey, action: KeyAction): Result<Unit> = Result.success(Unit)
        override suspend fun sendText(text: String): Result<Unit> = Result.success(Unit)
        override suspend fun sendPointer(event: PointerEvent): Result<Unit> = Result.success(Unit)
    }

    private val btTransport = TestTransport(TransportId.BLUETOOTH_HID, setOf(TvBrand.ANDROID_TV, TvBrand.LG_WEBOS))
    private val networkTransport = TestTransport(TransportId.NETWORK, setOf(TvBrand.ROKU, TvBrand.ANDROID_TV))
    private val selector = TransportSelector(listOf(btTransport, networkTransport))

    @Test
    fun `candidatesFor prioritises preferred transport when supported`() {
        val target = RemoteTarget(
            id = "test-1",
            displayName = "Android TV",
            brand = TvBrand.ANDROID_TV,
            preferredTransport = TransportId.NETWORK,
        )

        val candidates = selector.candidatesFor(target)
        assertThat(candidates).hasSize(2)
        assertThat(candidates[0].id).isEqualTo(TransportId.NETWORK)
        assertThat(candidates[1].id).isEqualTo(TransportId.BLUETOOTH_HID)
    }

    @Test
    fun `candidatesFor falls back to Bluetooth HID first if no preference`() {
        val target = RemoteTarget(
            id = "test-2",
            displayName = "Android TV",
            brand = TvBrand.ANDROID_TV,
        )

        val candidates = selector.candidatesFor(target)
        assertThat(candidates).hasSize(2)
        assertThat(candidates[0].id).isEqualTo(TransportId.BLUETOOTH_HID)
        assertThat(candidates[1].id).isEqualTo(TransportId.NETWORK)
    }

    @Test
    fun `candidatesFor returns only network transport for Roku target`() {
        val target = RemoteTarget(
            id = "test-3",
            displayName = "Roku Streaming Stick",
            brand = TvBrand.ROKU,
        )

        val candidates = selector.candidatesFor(target)
        assertThat(candidates).hasSize(1)
        assertThat(candidates[0].id).isEqualTo(TransportId.NETWORK)
    }
}
