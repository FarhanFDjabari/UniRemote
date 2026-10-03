package dev.djabari.uniremote.session

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.model.KeyAction
import dev.djabari.uniremote.model.PointerEvent
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.transport.bthid.BluetoothLinkEvents
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

class BluetoothEventRouterTest {
    private class Events : BluetoothLinkEvents {
        val values = mutableListOf<String>()
        override fun onBluetoothDisabled() { values += "disabled" }
        override fun onBondRemoved(address: String) { values += "bond:$address" }
        override fun onAclDisconnected(address: String) { values += "acl:$address" }
    }
    private class Profile(private val events: MutableList<String>) : HidProfileController {
        override fun open() = Unit
        override fun close() = Unit
        override suspend fun reopen() { events += "reopen" }
    }
    private class Session(private val events: MutableList<String>) : RemoteSession {
        override val state: StateFlow<TransportState> = MutableStateFlow(TransportState.Idle)
        override val capabilities: StateFlow<Set<TransportCapability>> = MutableStateFlow(emptySet())
        override val activeTarget: StateFlow<RemoteTarget?> = MutableStateFlow(null)
        override suspend fun connect(target: RemoteTarget) = Result.success(Unit)
        override suspend fun reconnect(): Result<Unit> { events += "reconnect"; return Result.success(Unit) }
        override suspend fun resetConnection() = Result.success(Unit)
        override suspend fun disconnect() = Unit
        override suspend fun press(key: RemoteKey, action: KeyAction) = Result.success(Unit)
        override suspend fun type(text: String) = Result.success(Unit)
        override suspend fun pointer(event: PointerEvent) = Result.success(Unit)
    }

    @Test fun `off and turning off notify link events`() = runTest {
        val events = Events()
        val router = BluetoothEventRouter(events, Profile(mutableListOf()), Session(mutableListOf()), backgroundScope)
        router.onAdapterStateChanged(BluetoothAdapter.STATE_OFF)
        router.onAdapterStateChanged(BluetoothAdapter.STATE_TURNING_OFF)
        assertThat(events.values).containsExactly("disabled", "disabled").inOrder()
    }

    @Test fun `adapter on reopens profile before reconnecting`() = runTest {
        val order = mutableListOf<String>()
        val router = BluetoothEventRouter(Events(), Profile(order), Session(order), backgroundScope)
        router.onAdapterStateChanged(BluetoothAdapter.STATE_ON)
        runCurrent()
        assertThat(order).containsExactly("reopen", "reconnect").inOrder()
    }

    @Test fun `bond none is routed while bonded is ignored`() = runTest {
        val events = Events()
        val router = BluetoothEventRouter(events, Profile(mutableListOf()), Session(mutableListOf()), backgroundScope)
        router.onBondStateChanged("AA:BB", BluetoothDevice.BOND_BONDED)
        router.onBondStateChanged("AA:BB", BluetoothDevice.BOND_NONE)
        assertThat(events.values).containsExactly("bond:AA:BB")
    }

    @Test fun `acl disconnect is routed`() = runTest {
        val events = Events()
        val router = BluetoothEventRouter(events, Profile(mutableListOf()), Session(mutableListOf()), backgroundScope)
        router.onAclDisconnected("AA:BB")
        assertThat(events.values).containsExactly("acl:AA:BB")
    }
}
