package dev.djabari.uniremote.transport.bthid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import dev.djabari.uniremote.model.KeyAction
import dev.djabari.uniremote.model.PointerDelta
import dev.djabari.uniremote.model.PointerEvent
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TransportId
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.transport.RemoteTransport
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportError
import dev.djabari.uniremote.transport.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bluetooth Classic HID Device transport. The phone registers as a composite HID
 * peripheral (keyboard + mouse + consumer control) that the TV pairs with without
 * requiring companion TV apps or pairing codes.
 */
class BluetoothHidTransport(
    private val proxy: HidDeviceProxy,
    private val adapter: BluetoothAdapter? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
    private val keepAliveIntervalMs: Long = KEEP_ALIVE_INTERVAL_MS,
) : RemoteTransport, BluetoothLinkEvents {

    override val id = TransportId.BLUETOOTH_HID

    private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<TransportCapability>>(emptySet())
    override val capabilities: StateFlow<Set<TransportCapability>> = _capabilities.asStateFlow()

    @Volatile
    private var connectedDevice: BluetoothDevice? = null

    @Volatile
    private var currentTarget: RemoteTarget? = null

    @Volatile
    private var isAppRegistered: Boolean = false

    @Volatile
    private var keepAliveJob: Job? = null

    /**
     * Serialises report writes. Prevents interleaving presses and releases which causes
     * stuck modifiers and uncontrolled repeat scrolling.
     */
    private val reportLock = Mutex()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isAppRegistered = registered
            if (registered) {
                val target = currentTarget
                if (target != null) {
                    val device = pluggedDevice ?: findBluetoothDevice(target.bluetoothAddress)
                    if (device != null) {
                        _state.value = TransportState.Connecting(target)
                        proxy.connect(device)
                    } else {
                        _state.value = TransportState.AwaitingHost
                    }
                } else {
                    _state.value = TransportState.AwaitingHost
                }
            } else {
                _capabilities.value = emptySet()
                // Deregistration also happens as part of failing a connect; keep the reason.
                if (_state.value !is TransportState.Failed) {
                    _state.value = TransportState.Idle
                }
            }
        }

        // Reached only from a live HID session, which cannot exist without BLUETOOTH_CONNECT.
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            val target = currentTarget ?: device?.let {
                RemoteTarget(
                    id = it.address,
                    displayName = it.name ?: "TV (${it.address})",
                    brand = dev.djabari.uniremote.model.TvBrand.GENERIC,
                    bluetoothAddress = it.address,
                )
            }

            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    if (target != null) {
                        currentTarget = target
                        _state.value = TransportState.Connected(target)
                        _capabilities.value = HID_CAPABILITIES
                        startKeepAlive()
                    }
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    if (target != null) {
                        _state.value = TransportState.Connecting(target)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasConnected = connectedDevice != null
                    stopKeepAlive()
                    connectedDevice = null
                    _capabilities.value = emptySet()
                    if (wasConnected) {
                        _state.value = TransportState.Failed(TransportError.CONNECTION_LOST)
                    } else if (currentTarget != null) {
                        _state.value = TransportState.AwaitingHost
                    } else {
                        _state.value = TransportState.Idle
                    }
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    _capabilities.value = emptySet()
                }
            }
        }
    }

    override fun supports(target: RemoteTarget): Boolean =
        target.brand.acceptsBluetoothHid && target.bluetoothAddress != null

    override suspend fun connect(target: RemoteTarget): Result<Unit> {
        if (!supports(target)) {
            val error = TransportError.UNSUPPORTED_TARGET
            _state.value = TransportState.Failed(error)
            return Result.failure(IllegalArgumentException(error.name))
        }

        currentTarget = target
        _state.value = TransportState.Preparing

        if (!proxy.awaitReady()) {
            _state.value = TransportState.Failed(TransportError.HID_REGISTRATION_FAILED)
            return Result.failure(IllegalStateException("Bluetooth HID profile is unavailable"))
        }

        val registered = proxy.registerApp(callback)
        // The framework holds the registration from the moment this returns true, whether or
        // not the status callback ever arrives — track it here or a failed connect leaks it.
        isAppRegistered = registered
        if (!registered) {
            val error = TransportError.HID_REGISTRATION_FAILED
            _state.value = TransportState.Failed(error)
            return Result.failure(IllegalStateException("HID registration failed on this device"))
        }

        val device = findBluetoothDevice(target.bluetoothAddress)
        if (device == null) {
            releaseRegistration()
            _state.value = TransportState.Failed(TransportError.TARGET_UNREACHABLE)
            return Result.failure(IllegalStateException("No bonded Bluetooth device for ${target.bluetoothAddress}"))
        }

        _state.value = TransportState.Connecting(target)
        proxy.connect(device)

        // The HID link is established by the framework asynchronously. Report success only
        // once the host is actually connected — otherwise the session can never fall back
        // to the network transport for a TV that silently refuses the HID role.
        val settled = withTimeoutOrNull(connectTimeoutMs) {
            state.first { it is TransportState.Connected || it is TransportState.Failed }
        }

        return when (settled) {
            is TransportState.Connected -> Result.success(Unit)
            is TransportState.Failed -> {
                releaseRegistration()
                Result.failure(IllegalStateException(settled.reason.name))
            }
            else -> {
                releaseRegistration()
                _state.value = TransportState.Failed(TransportError.HOST_REJECTED)
                Result.failure(IllegalStateException("TV did not accept the HID connection in time"))
            }
        }
    }

    override suspend fun disconnect() {
        stopKeepAlive()
        releaseAll()
        connectedDevice?.let { proxy.disconnect(it) }
        connectedDevice = null
        currentTarget = null
        if (isAppRegistered) {
            proxy.unregisterApp()
            isAppRegistered = false
        }
        _capabilities.value = emptySet()
        _state.value = TransportState.Idle
    }

    override suspend fun sendKey(key: RemoteKey, action: KeyAction): Result<Unit> {
        val binding = bindingFor(key)
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
                    val stroke = AsciiKeyMap.stroke(char)
                        ?: throw IllegalArgumentException("Cannot map character '$char'")
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
                write(HidDescriptor.REPORT_ID_MOUSE, HidReport.mouse(mask, dev.djabari.uniremote.model.PointerDelta.IDLE))
            }
            is PointerEvent.Scroll ->
                write(
                    HidDescriptor.REPORT_ID_MOUSE,
                    HidReport.mouse(0, dev.djabari.uniremote.model.PointerDelta.clamped(0, 0, event.ticks)),
                )
        }
    }

    override fun onBluetoothDisabled() = Unit

    override fun onBondRemoved(address: String) = Unit

    override fun onAclDisconnected(address: String) = Unit

    /**
     * A registration held by a transport nobody is using blocks the next one: the framework
     * allows a single HID app per process, so give it back as soon as a connect attempt fails.
     */
    private fun releaseRegistration() {
        if (isAppRegistered) {
            proxy.unregisterApp()
            isAppRegistered = false
        }
    }

    /**
     * Tizen ignores Consumer `AC Back` and expects keyboard Escape instead; every other
     * ecosystem we support honours the consumer usage. Everything else maps identically.
     */
    private fun bindingFor(key: RemoteKey): HidBinding? =
        if (key == RemoteKey.BACK && currentTarget?.brand == TvBrand.SAMSUNG_TIZEN) {
            RemoteKeyMapping.BACK_FALLBACK
        } else {
            RemoteKeyMapping[key]
        }

    /**
     * Some TVs drop an idle HID link after 30-60s, so keep traffic on it.
     *
     * The filler is a zero mouse report, not an empty keyboard report: an empty keyboard
     * report means "all keys up" and would silently cancel a key held through
     * [KeyAction.PRESS].
     */
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(keepAliveIntervalMs)
                reportLock.withLock {
                    write(
                        HidDescriptor.REPORT_ID_MOUSE,
                        HidReport.mouse(buttons = 0, delta = PointerDelta.IDLE),
                    )
                }
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private fun releaseAll(): Result<Unit> = runCatching {
        write(HidDescriptor.REPORT_ID_KEYBOARD, HidReport.KEYBOARD_RELEASE)
        write(HidDescriptor.REPORT_ID_CONSUMER, HidReport.CONSUMER_RELEASE)
    }

    private fun write(reportId: Byte, data: ByteArray): Result<Unit> {
        val ok = proxy.sendReport(connectedDevice, reportId.toInt(), data)
        return if (ok) Result.success(Unit)
        else Result.failure(IllegalStateException(TransportError.CONNECTION_LOST.name))
    }

    private fun findBluetoothDevice(address: String?): BluetoothDevice? {
        if (address == null || adapter == null) return null
        return try {
            adapter.getRemoteDevice(address)
        } catch (_: Exception) {
            null
        }
    }

    /** Exposed callback for unit test harness */
    internal fun getCallback(): BluetoothHidDevice.Callback = callback

    companion object {
        /** How long the TV gets to accept the HID connection before we fall back. */
        const val CONNECT_TIMEOUT_MS = 15_000L

        /** Idle-link keep-alive cadence, below the ~30s at which TVs start dropping links. */
        const val KEEP_ALIVE_INTERVAL_MS = 20_000L

        /** Capabilities of a connected HID peripheral. */
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
