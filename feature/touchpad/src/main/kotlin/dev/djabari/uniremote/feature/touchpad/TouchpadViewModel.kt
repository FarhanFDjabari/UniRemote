package dev.djabari.uniremote.feature.touchpad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.djabari.uniremote.model.PointerButton
import dev.djabari.uniremote.model.PointerDelta
import dev.djabari.uniremote.model.PointerEvent
import dev.djabari.uniremote.session.RemoteSession
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TouchpadViewModel @Inject constructor(
    private val session: RemoteSession,
) : ViewModel() {

    val connection: StateFlow<TransportState> = session.state
    val capabilities: StateFlow<Set<TransportCapability>> = session.capabilities

    private val _sensitivity = MutableStateFlow(1.6f)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    /**
     * Conflated channel to ensure we never overwhelm the Bluetooth HID report buffer.
     * Motion reports flush at ~60-100Hz cadence. CONFLATED already implies
     * DROP_OLDEST — passing an explicit overflow policy is illegal and throws.
     */
    private val deltaChannel = Channel<PointerDelta>(capacity = Channel.CONFLATED)

    init {
        // Off the main dispatcher deliberately: this poll never stops while the ViewModel
        // lives, and on Dispatchers.Main it would leave the looper permanently non-idle,
        // which stalls Espresso and wakes the main thread 83 times a second for nothing.
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(12) // ~83 Hz cadence
                val delta = deltaChannel.tryReceive().getOrNull()
                if (delta != null && !delta.isIdle) {
                    session.pointer(PointerEvent.Move(delta))
                }
            }
        }
    }

    fun onPointerDelta(delta: PointerDelta) {
        deltaChannel.trySend(delta)
    }

    fun onTap() = viewModelScope.launch {
        session.pointer(PointerEvent.Button(PointerButton.LEFT, pressed = true))
        delay(30)
        session.pointer(PointerEvent.Button(PointerButton.LEFT, pressed = false))
    }

    fun onSecondaryTap() = viewModelScope.launch {
        session.pointer(PointerEvent.Button(PointerButton.RIGHT, pressed = true))
        delay(30)
        session.pointer(PointerEvent.Button(PointerButton.RIGHT, pressed = false))
    }

    fun onScroll(ticks: Int) = viewModelScope.launch {
        session.pointer(PointerEvent.Scroll(ticks))
    }

    fun setSensitivity(value: Float) {
        _sensitivity.value = value.coerceIn(0.5f, 3.5f)
    }
}
