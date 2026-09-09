package dev.djabari.uniremote.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.djabari.uniremote.model.KeyAction
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.session.RemoteSession
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val session: RemoteSession,
) : ViewModel() {

    val capabilities: StateFlow<Set<TransportCapability>> = session.capabilities
    val connection: StateFlow<TransportState> = session.state
    val activeTarget: StateFlow<RemoteTarget?> = session.activeTarget

    fun onKeyTap(key: RemoteKey) = viewModelScope.launch {
        session.press(key, KeyAction.TAP)
    }

    fun onKeyPress(key: RemoteKey) = viewModelScope.launch {
        session.press(key, KeyAction.PRESS)
    }

    fun onKeyRelease(key: RemoteKey) = viewModelScope.launch {
        session.press(key, KeyAction.RELEASE)
    }
}
