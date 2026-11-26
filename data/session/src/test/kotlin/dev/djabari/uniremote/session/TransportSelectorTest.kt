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
    private val selector = TransportSelector(listOf(btTransport))

    @Test
    fun `candidatesFor returns the transport that supports the target`() {
        val target = RemoteTarget(
            id = "test-1",
            displayName = "Android TV",
            brand = TvBrand.ANDROID_TV,
        )

        assertThat(selector.candidatesFor(target)).containsExactly(btTransport)
    }

    @Test
    fun `candidatesFor returns nothing for a target no transport supports`() {
        val target = RemoteTarget(
            id = "test-2",
            displayName = "Roku Streaming Stick",
            brand = TvBrand.ROKU,
        )

        assertThat(selector.candidatesFor(target)).isEmpty()
    }
}
