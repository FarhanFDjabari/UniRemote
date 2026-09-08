package dev.djabari.tvremote.transport.bthid

import dev.djabari.tvremote.model.KeyAction
import dev.djabari.tvremote.model.PointerEvent
import dev.djabari.tvremote.model.RemoteKey
import dev.djabari.tvremote.model.RemoteTarget
import dev.djabari.tvremote.model.TransportId
import dev.djabari.tvremote.transport.RemoteTransport
import dev.djabari.tvremote.transport.TransportCapability
import dev.djabari.tvremote.transport.TransportError
import dev.djabari.tvremote.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bluetooth Classic HID Device transport. The phone becomes a keyboard + mouse the TV
 * pairs with like any other BT accessory — no app on the TV, no pairing code.
 *
 * PHASE 1 SKELETON. The report plumbing below is real; registration, the framework
 * callback wiring and the reconnect policy are the parts to fill in, in that order.
 */
class BluetoothHidTransport(
    private val proxy: HidDeviceProxy,
) : RemoteTransport {

    override val id = TransportId.BLUETOOTH_HID

    private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<TransportCapability>>(emptySet())
    override val capabilities: StateFlow<Set<TransportCapability>> = _capabilities.asStateFlow()

    /**
     * Serialises report writes. Two coroutines interleaving a press and a release is how
     * you end up with a modifier stuck down and a TV that scrolls forever.
     */
    private val reportLock = Mutex()

    override fun supports(target: RemoteTarget): Boolean =
        target.brand.acceptsBluetoothHid && target.bluetoothAddress != null

    override suspend fun connect(target: RemoteTarget): Result<Unit> {
        // TODO(phase1): getProfileProxy(HID_DEVICE) -> registerApp(sdp, qos, qos, executor, callback)
        //   - registerApp returning false is TransportError.HID_REGISTRATION_FAILED
        //     (some OEM builds ship this profile broken; fall through to :transport:network)
        //   - move to AwaitingHost and let the TV initiate; only some hosts accept our connect()
        //   - on Callback.onConnectionStateChanged(CONNECTED) -> Connected + publish capabilities
        return Result.failure(NotImplementedError("phase 1"))
    }

    override suspend fun disconnect() {
        // TODO(phase1): release all held keys FIRST, then unregisterApp(), then closeProfileProxy().
        //   Leaking a registration can wedge the profile until the phone reboots.
        releaseAll()
        _capabilities.value = emptySet()
        _state.value = TransportState.Idle
    }

    override suspend fun sendKey(key: RemoteKey, action: KeyAction): Result<Unit> {
        val binding = RemoteKeyMapping[key]
            ?: return Result.failure(IllegalArgumentException("$key has no HID binding"))

        return reportLock.withLock {
            when (binding) {
                is HidBinding.Keyboard -> when (action) {
                    KeyAction.TAP -> {
                        write(HidDescriptor.REPORT_ID_KEYBOARD, HidReport.keyboard(binding.modifiers, binding.usage))
                        write(HidDescriptor.REPORT_ID_KEYBOARD, HidReport.KEYBOARD_RELEASE)
                    }
                    KeyAction.PRESS ->
                        write(HidDescriptor.REPORT_ID_KEYBOARD, HidReport.keyboard(binding.modifiers, binding.usage))
                    KeyAction.RELEASE ->
                        write(HidDescriptor.REPORT_ID_KEYBOARD, HidReport.KEYBOARD_RELEASE)
                }

                is HidBinding.Consumer -> when (action) {
                    KeyAction.TAP -> {
                        write(HidDescriptor.REPORT_ID_CONSUMER, HidReport.consumer(binding.usage))
                        write(HidDescriptor.REPORT_ID_CONSUMER, HidReport.CONSUMER_RELEASE)
                    }
                    KeyAction.PRESS ->
                        write(HidDescriptor.REPORT_ID_CONSUMER, HidReport.consumer(binding.usage))
                    KeyAction.RELEASE ->
                        write(HidDescriptor.REPORT_ID_CONSUMER, HidReport.CONSUMER_RELEASE)
                }
            }
        }
    }

    override suspend fun sendText(text: String): Result<Unit> {
        val bad = AsciiKeyMap.untypeable(text)
        if (bad.isNotEmpty()) {
            return Result.failure(
                UnsupportedOperationException("HID cannot express: ${bad.joinToString()}")
            )
        }
        return reportLock.withLock {
            runCatching {
                text.forEach { char ->
                    val stroke = AsciiKeyMap.stroke(char)!!
                    write(HidDescriptor.REPORT_ID_KEYBOARD, HidReport.keyboard(stroke.modifiers, stroke.usage))
                    write(HidDescriptor.REPORT_ID_KEYBOARD, HidReport.KEYBOARD_RELEASE)
                }
            }
        }
    }

    override suspend fun sendPointer(event: PointerEvent): Result<Unit> = reportLock.withLock {
        when (event) {
            is PointerEvent.Move ->
                write(HidDescriptor.REPORT_ID_MOUSE, HidReport.mouse(buttons = 0, delta = event.delta))
            is PointerEvent.Button -> {
                val mask = if (event.pressed) 1 shl event.button.ordinal else 0
                write(HidDescriptor.REPORT_ID_MOUSE, HidReport.mouse(mask, dev.djabari.tvremote.model.PointerDelta.IDLE))
            }
            is PointerEvent.Scroll ->
                write(
                    HidDescriptor.REPORT_ID_MOUSE,
                    HidReport.mouse(0, dev.djabari.tvremote.model.PointerDelta.clamped(0, 0, event.ticks)),
                )
        }
    }

    private fun releaseAll(): Result<Unit> = runCatching {
        write(HidDescriptor.REPORT_ID_KEYBOARD, HidReport.KEYBOARD_RELEASE)
        write(HidDescriptor.REPORT_ID_CONSUMER, HidReport.CONSUMER_RELEASE)
    }

    private fun write(reportId: Byte, data: ByteArray): Result<Unit> {
        // TODO(phase1): pass the connected host BluetoothDevice instead of null once the
        //   callback has handed one over; null means "the single registered host".
        val ok = proxy.sendReport(null, reportId.toInt(), data)
        return if (ok) Result.success(Unit)
        else Result.failure(IllegalStateException(TransportError.CONNECTION_LOST.name))
    }

    companion object {
        /** What a live HID link can do. Note the absence of POWER_ON and UNICODE_TEXT. */
        val HID_CAPABILITIES = setOf(
            TransportCapability.DPAD,
            TransportCapability.NUMPAD,
            TransportCapability.VOLUME,
            TransportCapability.CHANNEL,
            TransportCapability.MEDIA_KEYS,
            TransportCapability.POINTER,
            TransportCapability.TEXT_INPUT,
            TransportCapability.POWER_OFF,
        )
    }
}
