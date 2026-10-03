package dev.djabari.uniremote.transport.bthid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.model.KeyAction
import dev.djabari.uniremote.model.PointerButton
import dev.djabari.uniremote.model.PointerDelta
import dev.djabari.uniremote.model.PointerEvent
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.transport.TransportError
import dev.djabari.uniremote.transport.TransportState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BluetoothHidTransportTest {

    private class FakeHidDeviceProxy : HidDeviceProxy {
        var registeredCallback: BluetoothHidDevice.Callback? = null
        var isRegistered = false
        val sentReports = mutableListOf<Pair<Int, ByteArray>>()
        var connectedDevice: BluetoothDevice? = null
        var connectResult = true
        var sendResult = true
        var connectCalls = 0
        var registerCalls = 0
        var unregisterCalls = 0
        var autoRegisterCallback = true
        var autoConnectCallback = false
        var deferUnregisterCallback = false
        var disconnectCallbackDuringUnregister = false
        var device: BluetoothDevice? = null
        var lastConnectedDevice: BluetoothDevice? = null
        private var pendingUnregisterCallback: BluetoothHidDevice.Callback? = null
        var onConnect: ((BluetoothDevice, Int) -> Unit)? = null

        var isReady = true

        fun deliverPendingUnregisterCallback() {
            pendingUnregisterCallback?.onAppStatusChanged(null, false)
            pendingUnregisterCallback = null
        }

        override suspend fun awaitReady(timeoutMs: Long): Boolean = isReady

        override fun registerApp(callback: BluetoothHidDevice.Callback): Boolean {
            registeredCallback = callback
            registerCalls++
            if (isRegistered) return false
            isRegistered = true
            if (autoRegisterCallback) callback.onAppStatusChanged(null, true)
            return true
        }

        override fun unregisterApp(): Boolean {
            unregisterCalls++
            isRegistered = false
            if (disconnectCallbackDuringUnregister) {
                registeredCallback?.onConnectionStateChanged(
                    lastConnectedDevice,
                    BluetoothProfile.STATE_DISCONNECTED,
                )
            }
            if (deferUnregisterCallback) {
                pendingUnregisterCallback = registeredCallback
            } else {
                registeredCallback?.onAppStatusChanged(null, false)
            }
            return true
        }

        override fun sendReport(device: BluetoothDevice?, id: Int, data: ByteArray): Boolean {
            sentReports.add(id to data.copyOf())
            return sendResult
        }

        override fun connect(device: BluetoothDevice): Boolean {
            connectCalls++
            connectedDevice = device
            lastConnectedDevice = device
            onConnect?.invoke(device, connectCalls)
            if (autoConnectCallback) {
                registeredCallback?.onConnectionStateChanged(device, BluetoothProfile.STATE_CONNECTED)
            }
            return connectResult
        }

        override fun disconnect(device: BluetoothDevice): Boolean {
            connectedDevice = null
            return true
        }
    }

    private val fakeProxy = FakeHidDeviceProxy()
    private lateinit var adapter: BluetoothAdapter
    private lateinit var transport: BluetoothHidTransport

    private val bondedTarget = RemoteTarget(
        id = "AA:BB:CC:DD:EE:FF",
        displayName = "Sony Bravia",
        brand = TvBrand.ANDROID_TV,
        bluetoothAddress = "AA:BB:CC:DD:EE:FF",
    )

    @Before
    fun setUp() {
        adapter = RuntimeEnvironment.getApplication().getSystemService(BluetoothManager::class.java).adapter
        shadowOf(adapter).setEnabled(true)
        val device = adapter.getRemoteDevice(bondedTarget.bluetoothAddress!!)
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)
        fakeProxy.device = device
        transport = BluetoothHidTransport(fakeProxy, adapter = adapter)
    }

    @Test
    fun `connect registers the app with the Bluetooth HID framework`() = runTest {
        fakeProxy.autoConnectCallback = true
        transport.connect(bondedTarget)

        assertThat(fakeProxy.registeredCallback).isNotNull()
    }

    @Test
    fun `a registration leaked by an earlier session is reclaimed`() = runTest {
        fakeProxy.isRegistered = true
        fakeProxy.autoConnectCallback = true

        val result = transport.connect(bondedTarget)

        assertThat(result.isSuccess).isTrue()
        assertThat(fakeProxy.registerCalls).isEqualTo(2)
        assertThat(fakeProxy.connectCalls).isEqualTo(1)
    }

    @Test
    fun `late unregister callback does not fail the retried registration`() = runTest {
        fakeProxy.isRegistered = true
        fakeProxy.deferUnregisterCallback = true
        fakeProxy.autoRegisterCallback = false
        fakeProxy.autoConnectCallback = true
        val job = launch { transport.connect(bondedTarget) }
        runCurrent()
        advanceTimeBy(BluetoothHidTransport.UNREGISTER_TIMEOUT_MS)
        runCurrent()

        fakeProxy.deliverPendingUnregisterCallback()
        fakeProxy.registeredCallback!!.onAppStatusChanged(null, true)
        runCurrent()
        job.join()

        assertThat(job.isCompleted).isTrue()
        assertThat(transport.state.value).isEqualTo(TransportState.Connected(bondedTarget))
    }

    @Test
    fun `link loss releases registration and a later connect succeeds`() = runTest {
        fakeProxy.autoConnectCallback = true
        assertThat(transport.connect(bondedTarget).isSuccess).isTrue()
        transport.getCallback().onConnectionStateChanged(fakeProxy.device, BluetoothProfile.STATE_DISCONNECTED)
        assertThat(fakeProxy.isRegistered).isFalse()

        assertThat(transport.connect(bondedTarget).isSuccess).isTrue()
        assertThat(fakeProxy.connectCalls).isEqualTo(2)
    }

    @Test
    fun `connecting again tears down the old link before unregister callback`() = runTest {
        fakeProxy.autoConnectCallback = true
        assertThat(transport.connect(bondedTarget).isSuccess).isTrue()
        fakeProxy.disconnectCallbackDuringUnregister = true

        val result = transport.connect(bondedTarget)

        assertThat(result.isSuccess).isTrue()
        assertThat(fakeProxy.connectCalls).isEqualTo(2)
        assertThat(transport.state.value).isEqualTo(TransportState.Connected(bondedTarget))
    }

    @Test
    fun `connect happens once and only after registration callback`() = runTest {
        fakeProxy.autoConnectCallback = true
        val result = transport.connect(bondedTarget)

        assertThat(result.isSuccess).isTrue()
        assertThat(fakeProxy.connectCalls).isEqualTo(1)
    }

    @Test
    fun `connect waits while registration callback is withheld`() = runTest {
        fakeProxy.autoRegisterCallback = false
        fakeProxy.autoConnectCallback = true
        val job = launch { transport.connect(bondedTarget) }
        runCurrent()

        assertThat(fakeProxy.connectCalls).isEqualTo(0)
        fakeProxy.registeredCallback!!.onAppStatusChanged(null, true)
        runCurrent()

        assertThat(fakeProxy.connectCalls).isEqualTo(1)
        job.join()
    }

    @Test
    fun `missing registration callback releases app and fails`() = runTest {
        fakeProxy.autoRegisterCallback = false

        val result = transport.connect(bondedTarget)

        assertThat(result.isFailure).isTrue()
        assertThat(transport.state.value).isEqualTo(TransportState.Failed(TransportError.HID_REGISTRATION_FAILED))
        assertThat(fakeProxy.isRegistered).isFalse()
        assertThat(fakeProxy.connectCalls).isEqualTo(0)
    }

    @Test
    fun `bluetooth disabled fails before touching the HID proxy`() = runTest {
        shadowOf(adapter).setEnabled(false)

        val result = transport.connect(bondedTarget)

        assertThat(result.isFailure).isTrue()
        assertThat(transport.state.value).isEqualTo(TransportState.Failed(TransportError.BLUETOOTH_DISABLED))
        assertThat(fakeProxy.registerCalls).isEqualTo(0)
    }

    @Test
    fun `unbonded target reports not paired`() = runTest {
        shadowOf(fakeProxy.device!!).setBondState(BluetoothDevice.BOND_NONE)

        val result = transport.connect(bondedTarget)

        assertThat(result.isFailure).isTrue()
        assertThat(transport.state.value).isEqualTo(TransportState.Failed(TransportError.NOT_PAIRED))
    }

    @Test
    fun `disconnected first attempt retries and second connects`() = runTest {
        fakeProxy.onConnect = { device, attempt ->
            if (attempt == 1) {
                fakeProxy.registeredCallback?.onConnectionStateChanged(
                    device,
                    BluetoothProfile.STATE_DISCONNECTED,
                )
            }
            else fakeProxy.registeredCallback?.onConnectionStateChanged(device, BluetoothProfile.STATE_CONNECTED)
        }

        val result = transport.connect(bondedTarget)

        assertThat(result.isSuccess).isTrue()
        assertThat(fakeProxy.connectCalls).isEqualTo(2)
    }

    @Test
    fun `failed then successful connect sequence never enters awaiting host`() = runTest {
        val observed = mutableListOf<TransportState>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { transport.state.collect { observed += it } }
        fakeProxy.onConnect = { device, attempt ->
            if (attempt == 1) {
                fakeProxy.registeredCallback?.onConnectionStateChanged(
                    device,
                    BluetoothProfile.STATE_DISCONNECTED,
                )
            }
            else fakeProxy.registeredCallback?.onConnectionStateChanged(device, BluetoothProfile.STATE_CONNECTED)
        }

        transport.connect(bondedTarget)
        collector.cancel()

        assertThat(observed).doesNotContain(TransportState.AwaitingHost)
        assertThat(transport.state.value).isEqualTo(TransportState.Connected(bondedTarget))
    }

    @Test
    fun `three failed attempts release registration and report host rejected`() = runTest {
        fakeProxy.connectResult = false
        val shortAttempts = BluetoothHidTransport(fakeProxy, adapter, connectTimeoutMs = 5)

        val result = shortAttempts.connect(bondedTarget)

        assertThat(result.isFailure).isTrue()
        assertThat(shortAttempts.state.value).isEqualTo(TransportState.Failed(TransportError.HOST_REJECTED))
        assertThat(fakeProxy.connectCalls).isEqualTo(3)
        assertThat(fakeProxy.isRegistered).isFalse()
    }

    @Test
    fun `late disconnect after failed connect leaves state unchanged`() = runTest {
        fakeProxy.connectResult = false
        val shortAttempts = BluetoothHidTransport(fakeProxy, adapter, connectTimeoutMs = 5)
        shortAttempts.connect(bondedTarget)
        val failedState = shortAttempts.state.value

        shortAttempts.getCallback().onConnectionStateChanged(fakeProxy.device, BluetoothProfile.STATE_DISCONNECTED)

        assertThat(shortAttempts.state.value).isEqualTo(failedState)
    }

    @Test
    fun `three consecutive report failures signal connection lost`() = runTest {
        fakeProxy.autoConnectCallback = true
        assertThat(transport.connect(bondedTarget).isSuccess).isTrue()
        fakeProxy.sendResult = false

        repeat(3) { transport.sendPointer(PointerEvent.Move(PointerDelta.IDLE)) }

        assertThat(transport.state.value).isEqualTo(TransportState.Failed(TransportError.CONNECTION_LOST))
    }

    @Test
    fun `successful report resets consecutive failures`() = runTest {
        fakeProxy.autoConnectCallback = true
        transport.connect(bondedTarget)
        fakeProxy.sendResult = false
        repeat(2) { transport.sendPointer(PointerEvent.Move(PointerDelta.IDLE)) }
        fakeProxy.sendResult = true
        transport.sendPointer(PointerEvent.Move(PointerDelta.IDLE))
        fakeProxy.sendResult = false

        transport.sendPointer(PointerEvent.Move(PointerDelta.IDLE))

        assertThat(transport.state.value).isEqualTo(TransportState.Connected(bondedTarget))
    }

    @Test
    fun `disconnect after two failed writes never emits connection lost`() = runTest {
        val observed = mutableListOf<TransportState>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            transport.state.collect { observed += it }
        }
        fakeProxy.autoConnectCallback = true
        transport.connect(bondedTarget)
        fakeProxy.sendResult = false
        repeat(2) { transport.sendPointer(PointerEvent.Move(PointerDelta.IDLE)) }

        transport.disconnect()
        collector.cancel()

        assertThat(observed).doesNotContain(TransportState.Failed(TransportError.CONNECTION_LOST))
        assertThat(transport.state.value).isEqualTo(TransportState.Idle)
    }

    @Test
    fun `cancelling connect releases registration`() = runTest {
        val job = launch { transport.connect(bondedTarget) }
        runCurrent()

        job.cancel()
        runCurrent()

        assertThat(fakeProxy.isRegistered).isFalse()
    }

    @Test
    fun `callback for a different device is ignored`() = runTest {
        fakeProxy.autoConnectCallback = true
        transport.connect(bondedTarget)
        val otherDevice = adapter.getRemoteDevice("11:22:33:44:55:66")

        transport.getCallback().onConnectionStateChanged(otherDevice, BluetoothProfile.STATE_DISCONNECTED)

        assertThat(transport.state.value).isEqualTo(TransportState.Connected(bondedTarget))
    }

    @Test
    fun `bluetooth disabled clears link without unregistering`() = runTest {
        fakeProxy.autoConnectCallback = true
        transport.connect(bondedTarget)
        val unregistersBefore = fakeProxy.unregisterCalls
        fakeProxy.isRegistered = false

        transport.onBluetoothDisabled()

        assertThat(transport.state.value).isEqualTo(TransportState.Failed(TransportError.BLUETOOTH_DISABLED))
        assertThat(transport.capabilities.value).isEmpty()
        assertThat(fakeProxy.isRegistered).isFalse()
        assertThat(fakeProxy.unregisterCalls).isEqualTo(unregistersBefore)
    }

    @Test
    fun `bond removal releases registration and reports not paired`() = runTest {
        fakeProxy.autoConnectCallback = true
        transport.connect(bondedTarget)

        transport.onBondRemoved(bondedTarget.bluetoothAddress!!)

        assertThat(transport.state.value).isEqualTo(TransportState.Failed(TransportError.NOT_PAIRED))
        assertThat(transport.capabilities.value).isEmpty()
        assertThat(fakeProxy.isRegistered).isFalse()
    }

    @Test
    fun `ACL loss for connected target reports connection lost`() = runTest {
        fakeProxy.autoConnectCallback = true
        transport.connect(bondedTarget)

        transport.onAclDisconnected(bondedTarget.bluetoothAddress!!)

        assertThat(transport.state.value).isEqualTo(TransportState.Failed(TransportError.CONNECTION_LOST))
        assertThat(fakeProxy.isRegistered).isFalse()
    }

    @Test
    fun `connect waits for the framework profile instead of blaming the device`() = runTest {
        fakeProxy.isReady = false

        val result = transport.connect(bondedTarget)

        assertThat(result.isFailure).isTrue()
        assertThat(fakeProxy.registeredCallback).isNull()
        assertThat(transport.state.value)
            .isEqualTo(TransportState.Failed(TransportError.HID_REGISTRATION_FAILED))
    }

    @Test
    fun `a failed connect gives the HID registration back`() = runTest {
        transport.connect(bondedTarget)

        assertThat(fakeProxy.isRegistered).isFalse()
    }

    @Test
    fun `disabled adapter fails so the session can fall back`() = runTest {
        shadowOf(adapter).setEnabled(false)
        val result = transport.connect(bondedTarget)

        assertThat(result.isFailure).isTrue()
        assertThat(transport.state.value)
            .isEqualTo(TransportState.Failed(TransportError.BLUETOOTH_DISABLED))
    }

    @Test
    fun `Back uses keyboard Escape on Tizen instead of Consumer AC Back`() = runTest {
        fakeProxy.autoConnectCallback = true
        transport.connect(bondedTarget.copy(brand = TvBrand.SAMSUNG_TIZEN))
        fakeProxy.sentReports.clear()

        transport.sendKey(RemoteKey.BACK, KeyAction.TAP)

        val (id, report) = fakeProxy.sentReports.first()
        assertThat(id).isEqualTo(HidDescriptor.REPORT_ID_KEYBOARD.toInt())
        assertThat(report[2].toInt()).isEqualTo(KeyboardUsage.ESCAPE)
    }

    @Test
    fun `Back uses Consumer AC Back on Android TV`() = runTest {
        transport.connect(bondedTarget)
        fakeProxy.sentReports.clear()

        transport.sendKey(RemoteKey.BACK, KeyAction.TAP)

        val (id, report) = fakeProxy.sentReports.first()
        assertThat(id).isEqualTo(HidDescriptor.REPORT_ID_CONSUMER.toInt())
        assertThat(report[0].toInt() and 0xFF).isEqualTo(ConsumerUsage.AC_BACK and 0xFF)
        assertThat(report[1].toInt() and 0xFF).isEqualTo((ConsumerUsage.AC_BACK shr 8) and 0xFF)
    }

    @Test
    fun `sendKey TAP sends both key press and key release reports`() = runTest {
        val result = transport.sendKey(RemoteKey.DPAD_UP, KeyAction.TAP)
        assertThat(result.isSuccess).isTrue()

        // DPAD_UP is Keyboard usage 0x52 at index 2 (index 1 is reserved)
        assertThat(fakeProxy.sentReports).hasSize(2)
        val (firstId, firstReport) = fakeProxy.sentReports[0]
        assertThat(firstId).isEqualTo(HidDescriptor.REPORT_ID_KEYBOARD.toInt())
        assertThat(firstReport[2].toInt()).isEqualTo(0x52) // Usage DPAD_UP

        val (secondId, secondReport) = fakeProxy.sentReports[1]
        assertThat(secondId).isEqualTo(HidDescriptor.REPORT_ID_KEYBOARD.toInt())
        assertThat(secondReport).isEqualTo(HidReport.KEYBOARD_RELEASE)
    }

    @Test
    fun `sendKey for Consumer key sends Consumer report ID and release`() = runTest {
        val result = transport.sendKey(RemoteKey.VOLUME_UP, KeyAction.TAP)
        assertThat(result.isSuccess).isTrue()

        assertThat(fakeProxy.sentReports).hasSize(2)
        val (firstId, firstReport) = fakeProxy.sentReports[0]
        assertThat(firstId).isEqualTo(HidDescriptor.REPORT_ID_CONSUMER.toInt())
        assertThat(firstReport).isEqualTo(
            byteArrayOf(0xE9.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
        ) // Volume Up usage 0x00E9 LE (4 bytes)

        val (secondId, secondReport) = fakeProxy.sentReports[1]
        assertThat(secondId).isEqualTo(HidDescriptor.REPORT_ID_CONSUMER.toInt())
        assertThat(secondReport).isEqualTo(HidReport.CONSUMER_RELEASE)
    }

    @Test
    fun `sendText converts ASCII string to sequential press-release keyboard reports`() = runTest {
        val result = transport.sendText("AB")
        assertThat(result.isSuccess).isTrue()

        // 'A' = Shift + 0x04 press, release. 'B' = Shift + 0x05 press, release.
        assertThat(fakeProxy.sentReports).hasSize(4)
        assertThat(fakeProxy.sentReports[0].first).isEqualTo(HidDescriptor.REPORT_ID_KEYBOARD.toInt())
        assertThat(fakeProxy.sentReports[1].second).isEqualTo(HidReport.KEYBOARD_RELEASE)
        assertThat(fakeProxy.sentReports[2].first).isEqualTo(HidDescriptor.REPORT_ID_KEYBOARD.toInt())
        assertThat(fakeProxy.sentReports[3].second).isEqualTo(HidReport.KEYBOARD_RELEASE)
    }

    @Test
    fun `sendPointer Move sends mouse report with deltas`() = runTest {
        val result = transport.sendPointer(PointerEvent.Move(PointerDelta.clamped(15, -10)))
        assertThat(result.isSuccess).isTrue()

        assertThat(fakeProxy.sentReports).hasSize(1)
        val (id, data) = fakeProxy.sentReports[0]
        assertThat(id).isEqualTo(HidDescriptor.REPORT_ID_MOUSE.toInt())
        assertThat(data[0].toInt()).isEqualTo(0) // buttons
        assertThat(data[1].toInt()).isEqualTo(15) // dx
        assertThat(data[2].toInt()).isEqualTo(-10) // dy
    }

    @Test
    fun `sendPointer Button sends mouse report with button mask`() = runTest {
        val result = transport.sendPointer(PointerEvent.Button(PointerButton.LEFT, pressed = true))
        assertThat(result.isSuccess).isTrue()

        assertThat(fakeProxy.sentReports).hasSize(1)
        val (id, data) = fakeProxy.sentReports[0]
        assertThat(id).isEqualTo(HidDescriptor.REPORT_ID_MOUSE.toInt())
        assertThat(data[0].toInt()).isEqualTo(1) // primary button bit 0
    }

    @Test
    fun `a held key survives the idle keep-alive`() = runTest {
        val keepAlive = BluetoothHidTransport(
            fakeProxy,
            adapter = adapter,
            scope = backgroundScope,
            keepAliveIntervalMs = 100,
        )
        fakeProxy.autoConnectCallback = true
        keepAlive.connect(bondedTarget)
        keepAlive.sendKey(RemoteKey.DPAD_UP, KeyAction.PRESS)
        fakeProxy.sentReports.clear()

        advanceTimeBy(250)

        // Keep-alive traffic must be mouse no-ops; an empty keyboard report would release
        // the key the caller is still holding.
        assertThat(fakeProxy.sentReports).isNotEmpty()
        assertThat(fakeProxy.sentReports.map { it.first }.toSet())
            .containsExactly(HidDescriptor.REPORT_ID_MOUSE.toInt())
        assertThat(fakeProxy.sentReports.map { it.second.toList() }.toSet())
            .containsExactly(listOf<Byte>(0, 0, 0, 0))
    }

    @Test
    fun `disconnect emits keyboard and consumer release reports`() = runTest {
        transport.disconnect()

        assertThat(fakeProxy.sentReports).hasSize(2)
        assertThat(fakeProxy.sentReports[0].second).isEqualTo(HidReport.KEYBOARD_RELEASE)
        assertThat(fakeProxy.sentReports[1].second).isEqualTo(HidReport.CONSUMER_RELEASE)
    }
}
