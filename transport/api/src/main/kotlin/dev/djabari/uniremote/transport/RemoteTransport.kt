package dev.djabari.uniremote.transport

import dev.djabari.uniremote.model.PointerEvent
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.KeyAction
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TransportId
import kotlinx.coroutines.flow.StateFlow

/**
 * One way of talking to a TV.
 *
 * Feature modules never see an implementation of this — they go through
 * `:data:session`.
 */
interface RemoteTransport {

    val id: TransportId

    val state: StateFlow<TransportState>

    /**
     * What this transport can actually do *for the currently connected target*.
     * Empty while disconnected. The UI gates controls on this — never send a
     * command the transport has not claimed.
     */
    val capabilities: StateFlow<Set<TransportCapability>>

    /** True if this transport could plausibly reach [target] at all. Cheap, no I/O. */
    fun supports(target: RemoteTarget): Boolean

    suspend fun connect(target: RemoteTarget): Result<Unit>

    suspend fun disconnect()

    suspend fun sendKey(key: RemoteKey, action: KeyAction = KeyAction.TAP): Result<Unit>

    /** ASCII-only: HID can only type what a US keyboard layout can. */
    suspend fun sendText(text: String): Result<Unit>

    suspend fun sendPointer(event: PointerEvent): Result<Unit>
}

enum class TransportCapability {
    DPAD,
    NUMPAD,
    VOLUME,
    CHANNEL,
    MEDIA_KEYS,
    POINTER,
    TEXT_INPUT,
    POWER_OFF,
}

sealed interface TransportState {
    data object Idle : TransportState

    /** BT HID: registering the HID app with the framework. */
    data object Preparing : TransportState

    /** BT HID: registered and waiting for the TV to initiate. Show pairing instructions here. */
    data object AwaitingHost : TransportState

    data class Connecting(val target: RemoteTarget) : TransportState

    data class Connected(val target: RemoteTarget) : TransportState

    /**
     * Session-level: the link to [target] dropped and an automatic reconnect is scheduled.
     * [attempt] is 1-based and never exceeds [maxAttempts].
     */
    data class Reconnecting(val target: RemoteTarget, val attempt: Int, val maxAttempts: Int) : TransportState

    data class Failed(val reason: TransportError, val cause: Throwable? = null) : TransportState
}

enum class TransportError {
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    /** Registration returned false — some OEM builds ship a broken HID device profile. */
    HID_REGISTRATION_FAILED,
    HOST_REJECTED,
    /** BT HID: the target is no longer bonded with this phone, so it can only be paired again. */
    NOT_PAIRED,
    CONNECTION_LOST,
    TARGET_UNREACHABLE,
    UNSUPPORTED_TARGET,
    UNKNOWN,
}

/** Plain-language explanation of a failure with the next step to take, for the UI and notification. */
val TransportError.userMessage: String
    get() = when (this) {
        TransportError.BLUETOOTH_DISABLED -> "Bluetooth is off. Turn it on and try again."
        TransportError.PERMISSION_DENIED -> "UniRemote needs the Nearby devices permission to use Bluetooth. Allow it in Settings, then try again."
        TransportError.HID_REGISTRATION_FAILED -> "Couldn't start Bluetooth remote mode. Another app may be using this phone as a Bluetooth keyboard or mouse (for example \"Bluetooth Keyboard & Mouse\"). Close or force-stop it, then try again."
        TransportError.HOST_REJECTED -> "The TV didn't accept the connection. Tap Reset connection and try again. If it keeps failing, forget the TV in your phone's Bluetooth settings, remove this phone from the TV's Bluetooth devices, then pair them again."
        TransportError.NOT_PAIRED -> "This phone isn't paired with the TV anymore. Open Bluetooth settings and pair with the TV again, then come back to UniRemote."
        TransportError.CONNECTION_LOST -> "Lost the connection to the TV. Make sure it's on and in range, then tap Try again."
        TransportError.TARGET_UNREACHABLE -> "Couldn't reach the TV. Make sure it's on and nearby, then try again."
        TransportError.UNSUPPORTED_TARGET -> "This TV doesn't accept a Bluetooth remote, so UniRemote can't control it. Roku is one of these."
        TransportError.UNKNOWN -> "Something went wrong. Try again."
    }
