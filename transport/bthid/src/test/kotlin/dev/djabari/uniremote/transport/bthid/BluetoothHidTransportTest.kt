package dev.djabari.uniremote.transport.bthid

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothHidTransportTest {

    private class FakeHidDeviceProxy : HidDeviceProxy {
        var registeredCallback: BluetoothHidDevice.Callback? = null
        var isRegistered = false
        val sentReports = mutableListOf<Pair<Int, ByteArray>>()
        var connectedDevice: BluetoothDevice? = null

        var isReady = true

        override suspend fun awaitReady(timeoutMs: Long): Boolean = isReady

        override fun registerApp(callback: BluetoothHidDevice.Callback): Boolean {
            registeredCallback = callback
            isRegistered = true
            return true
        }

        override fun unregisterApp(): Boolean {
            isRegistered = false
            return true
        }

        override fun sendReport(device: BluetoothDevice?, id: Int, data: ByteArray): Boolean {
            sentReports.add(id to data.copyOf())
            return true
        }

        override fun connect(device: BluetoothDevice): Boolean {
            connectedDevice = device
            return true
        }

        override fun disconnect(device: BluetoothDevice): Boolean {
            connectedDevice = null
            return true
        }
    }

    private val fakeProxy = FakeHidDeviceProxy()
    private val transport = BluetoothHidTransport(fakeProxy, adapter = null)

    private val bondedTarget = RemoteTarget(
        id = "AA:BB:CC:DD:EE:FF",
        displayName = "Sony Bravia",
        brand = TvBrand.ANDROID_TV,
        bluetoothAddress = "AA:BB:CC:DD:EE:FF",
    )

    @Test
    fun `connect registers the app with the Bluetooth HID framework`() = runTest {
        transport.connect(bondedTarget)

        assertThat(fakeProxy.registeredCallback).isNotNull()
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
    fun `connect fails when the device cannot be resolved so the session can fall back`() = runTest {
        val result = transport.connect(bondedTarget)

        assertThat(result.isFailure).isTrue()
        assertThat(transport.state.value)
            .isEqualTo(TransportState.Failed(TransportError.TARGET_UNREACHABLE))
    }

    @Test
    fun `Back uses keyboard Escape on Tizen instead of Consumer AC Back`() = runTest {
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
        assertThat(firstReport).isEqualTo(byteArrayOf(0xE9.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())) // Volume Up usage 0x00E9 LE (4 bytes)

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
            adapter = null,
            scope = backgroundScope,
            keepAliveIntervalMs = 100,
        )
        // Fails on the unresolvable device, but leaves the transport pointed at the target
        // so the connection callback below can promote it to Connected.
        keepAlive.connect(bondedTarget)
        keepAlive.sendKey(RemoteKey.DPAD_UP, KeyAction.PRESS)
        keepAlive.getCallback().onConnectionStateChanged(null, BluetoothProfile.STATE_CONNECTED)
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
