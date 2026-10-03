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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val connectTimeoutMs: Long = ATTEMPT_TIMEOUT_MS,
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

    @Volatile
    private var registrationSignal: CompletableDeferred<Boolean>? = null

    @Volatile
    private var unregisterSignal: CompletableDeferred<Unit>? = null

    @Volatile
    private var attemptSignal: CompletableDeferred<Boolean>? = null

    @Volatile
    private var attemptInFlight: Boolean = false

    @Volatile
    private var consecutiveWriteFailures: Int = 0

    @Volatile
    private var externalFailure: TransportError? = null

    /**
     * Serialises report writes. Prevents interleaving presses and releases which causes
     * stuck modifiers and uncontrolled repeat scrolling.
     */
    private val reportLock = Mutex()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (registered) {
                isAppRegistered = true
                registrationSignal?.complete(true)
            } else {
                if (unregisterSignal != null || registrationSignal?.isCompleted != true) {
                    isAppRegistered = false
                }
                unregisterSignal?.complete(Unit)
            }
        }

        // Reached only from a live HID session, which cannot exist without BLUETOOTH_CONNECT.
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            val target = currentTarget ?: return
            val expected = target.bluetoothAddress ?: return
            if (device?.address != expected) return
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    consecutiveWriteFailures = 0
                    _state.value = TransportState.Connected(target)
                    _capabilities.value = HID_CAPABILITIES
                    attemptInFlight = false
                    attemptSignal?.complete(true)
                    startKeepAlive()
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    if (attemptInFlight) {
                        _state.value = TransportState.Connecting(target)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> when {
                    connectedDevice != null -> handleLinkLoss(TransportError.CONNECTION_LOST)
                    attemptInFlight -> attemptSignal?.complete(false)
                }
            }
        }
    }

    override fun supports(target: RemoteTarget): Boolean =
        target.brand.acceptsBluetoothHid && target.bluetoothAddress != null

    /**
     * The HID link is established asynchronously by the framework. Report success only once
     * the host is connected so the session can fall back when a TV silently refuses HID.
     */
    @SuppressLint("MissingPermission")
    override suspend fun connect(target: RemoteTarget): Result<Unit> {
        if (!supports(target)) return fail(TransportError.UNSUPPORTED_TARGET)
        _state.value = TransportState.Preparing
        val btAdapter: BluetoothAdapter
        val device: BluetoothDevice
        try {
            btAdapter = adapter ?: return fail(TransportError.BLUETOOTH_DISABLED)
            if (!btAdapter.isEnabled) return fail(TransportError.BLUETOOTH_DISABLED)
            device = btAdapter.getRemoteDevice(target.bluetoothAddress!!)
            if (device.bondState != BluetoothDevice.BOND_BONDED) return fail(TransportError.NOT_PAIRED)
        } catch (_: SecurityException) {
            return fail(TransportError.PERMISSION_DENIED)
        } catch (_: IllegalArgumentException) {
            return fail(TransportError.TARGET_UNREACHABLE)
        }

        // Clear the old link before switching targets so its disconnect callback is stale.
        connectedDevice?.let { oldDevice ->
            stopKeepAlive()
            connectedDevice = null
            _capabilities.value = emptySet()
            proxy.disconnect(oldDevice)
        }
        currentTarget = target
        externalFailure = null
        try {
            if (isAppRegistered) unregisterAndWait()
            externalFailure?.let { return fail(it) }
            if (!proxy.awaitReady()) return fail(TransportError.HID_REGISTRATION_FAILED)
            externalFailure?.let { return fail(it) }
            registrationSignal = CompletableDeferred()
            var registered = proxy.registerApp(callback)
            if (registered) isAppRegistered = true else {
                // A stale framework registration can make the first call fail despite no local flag.
                unregisterAndWait()
                registrationSignal = CompletableDeferred()
                registered = proxy.registerApp(callback)
                if (registered) isAppRegistered = true
            }
            if (!registered) return fail(TransportError.HID_REGISTRATION_FAILED)
            val registrationComplete = withTimeoutOrNull(REGISTER_TIMEOUT_MS) {
                registrationSignal!!.await()
            }
            externalFailure?.let {
                releaseRegistration()
                return fail(it)
            }
            if (registrationComplete != true) {
                releaseRegistration()
                return fail(TransportError.HID_REGISTRATION_FAILED)
            }
            consecutiveWriteFailures = 0
            repeat(CONNECT_ATTEMPTS) { index ->
                attemptInFlight = true
                attemptSignal = CompletableDeferred()
                _state.value = TransportState.Connecting(target)
                // The callback signals this attempt; connect() alone calls proxy.connect().
                if (!proxy.connect(device)) attemptSignal?.complete(false)
                val connected = withTimeoutOrNull(connectTimeoutMs) { attemptSignal!!.await() } == true
                attemptInFlight = false
                if (connected) return Result.success(Unit)
                externalFailure?.let {
                    releaseRegistration()
                    currentTarget = null
                    return fail(it)
                }
                if (index < CONNECT_ATTEMPTS - 1) {
                    delay(RETRY_BACKOFF_MS * (index + 1))
                    proxy.disconnect(device)
                }
            }
            releaseRegistration()
            // Clearing the target makes callbacks arriving after failure inert.
            currentTarget = null
            _state.value = TransportState.Failed(TransportError.HOST_REJECTED)
            return Result.failure(IllegalStateException(TransportError.HOST_REJECTED.name))
        } catch (cancelled: CancellationException) {
            attemptInFlight = false
            attemptSignal?.cancel()
            attemptSignal = null
            currentTarget = null
            releaseRegistration()
            throw cancelled
        } catch (security: SecurityException) {
            releaseRegistration()
            return fail(TransportError.PERMISSION_DENIED)
        }
    }

    private suspend fun unregisterAndWait() {
        val signal = CompletableDeferred<Unit>()
        unregisterSignal = signal
        proxy.unregisterApp()
        withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) { signal.await() }
        isAppRegistered = false
        unregisterSignal = null
    }

    override suspend fun disconnect() {
        attemptInFlight = false
        attemptSignal?.cancel()
        attemptSignal = null
        stopKeepAlive()
        val device = connectedDevice
        connectedDevice = null
        currentTarget = null
        releaseAll(device)
        device?.let { proxy.disconnect(it) }
        consecutiveWriteFailures = 0
        releaseRegistration()
        _capabilities.value = emptySet()
        _state.value = TransportState.Idle
    }

    override suspend fun sendKey(key: RemoteKey, action: KeyAction): Result<Unit> {
        val binding = bindingFor(key) ?: return Result.failure(IllegalArgumentException("$key has no HID binding"))

        return reportLock.withLock {
            when (binding) {
                is HidBinding.Keyboard -> when (action) {
                    KeyAction.TAP -> {
                        write(
                            HidDescriptor.REPORT_ID_KEYBOARD,
                            HidReport.keyboard(binding.modifiers, binding.usage),
                        )
                        write(HidDescriptor.REPORT_ID_KEYBOARD, HidReport.KEYBOARD_RELEASE)
                    }
                    KeyAction.PRESS ->
                        write(
                            HidDescriptor.REPORT_ID_KEYBOARD,
                            HidReport.keyboard(binding.modifiers, binding.usage),
                        )
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
                    write(
                        HidDescriptor.REPORT_ID_KEYBOARD,
                        HidReport.keyboard(stroke.modifiers, stroke.usage),
                    )
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
                write(
                    HidDescriptor.REPORT_ID_MOUSE,
                    HidReport.mouse(mask, PointerDelta.IDLE),
                )
            }
            is PointerEvent.Scroll ->
                write(
                    HidDescriptor.REPORT_ID_MOUSE,
                    HidReport.mouse(0, PointerDelta.clamped(0, 0, event.ticks)),
                )
        }
    }

    override fun onBluetoothDisabled() {
        val wasIdleWithoutTarget = _state.value == TransportState.Idle && currentTarget == null
        externalFailure = TransportError.BLUETOOTH_DISABLED
        attemptInFlight = false
        attemptSignal?.complete(false)
        registrationSignal?.complete(false)
        stopKeepAlive()
        connectedDevice = null
        _capabilities.value = emptySet()
        isAppRegistered = false
        currentTarget = null
        if (!wasIdleWithoutTarget) _state.value = TransportState.Failed(TransportError.BLUETOOTH_DISABLED)
    }

    override fun onBondRemoved(address: String) {
        if (currentTarget?.bluetoothAddress == address) {
            externalFailure = TransportError.NOT_PAIRED
            attemptInFlight = false
            attemptSignal?.complete(false)
            registrationSignal?.complete(false)
            handleLinkLoss(TransportError.NOT_PAIRED)
        }
    }

    override fun onAclDisconnected(address: String) {
        if (connectedDevice?.address == address) handleLinkLoss(TransportError.CONNECTION_LOST)
    }

    private fun handleLinkLoss(reason: TransportError) {
        val device = connectedDevice
        if (device == null && _state.value is TransportState.Failed) return
        if (reason == TransportError.CONNECTION_LOST &&
            consecutiveWriteFailures >= MAX_CONSECUTIVE_WRITE_FAILURES
        ) {
            device?.let { proxy.disconnect(it) }
        }
        attemptInFlight = false
        attemptSignal?.complete(false)
        stopKeepAlive()
        connectedDevice = null
        _capabilities.value = emptySet()
        releaseRegistration()
        currentTarget = null
        _state.value = TransportState.Failed(reason)
    }

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

    private fun fail(error: TransportError): Result<Unit> {
        attemptInFlight = false
        // Clearing the target makes callbacks arriving after failure inert.
        currentTarget = null
        releaseRegistration()
        _capabilities.value = emptySet()
        _state.value = TransportState.Failed(error)
        return Result.failure(IllegalStateException(error.name))
    }

    /**
     * Tizen ignores Consumer `AC Back` and expects keyboard Escape instead. Every other
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

    private fun releaseAll(device: BluetoothDevice?) {
        write(HidDescriptor.REPORT_ID_KEYBOARD, HidReport.KEYBOARD_RELEASE, device)
        write(HidDescriptor.REPORT_ID_CONSUMER, HidReport.CONSUMER_RELEASE, device)
    }

    private fun write(
        id: Byte,
        data: ByteArray,
        device: BluetoothDevice? = connectedDevice,
    ): Result<Unit> {
        val ok = proxy.sendReport(device, id.toInt(), data)
        consecutiveWriteFailures = if (ok) 0 else consecutiveWriteFailures + 1
        if (!ok && consecutiveWriteFailures >= MAX_CONSECUTIVE_WRITE_FAILURES && connectedDevice != null) {
            handleLinkLoss(TransportError.CONNECTION_LOST)
        }
        return if (ok) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(TransportError.CONNECTION_LOST.name))
        }
    }

    /** Exposed callback for unit test harness */
    internal fun getCallback(): BluetoothHidDevice.Callback = callback

    companion object {
        /** Maximum time to wait for each host connection attempt. */
        const val ATTEMPT_TIMEOUT_MS = 8_000L

        /** Maximum time to wait for the framework's registration callback. */
        const val REGISTER_TIMEOUT_MS = 3_000L

        /** Maximum time to wait for an existing framework registration to be released. */
        const val UNREGISTER_TIMEOUT_MS = 1_000L

        /** Number of connection attempts before returning HOST_REJECTED. */
        const val CONNECT_ATTEMPTS = 3

        /** Linear delay multiplier between connection attempts. */
        const val RETRY_BACKOFF_MS = 1_000L

        /** Failed report writes tolerated before dropping a connected link. */
        const val MAX_CONSECUTIVE_WRITE_FAILURES = 3

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
