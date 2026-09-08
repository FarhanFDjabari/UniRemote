package dev.djabari.tvremote.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.djabari.tvremote.model.KeyAction
import dev.djabari.tvremote.model.RemoteKey
import dev.djabari.tvremote.session.RemoteSession
import dev.djabari.tvremote.transport.TransportCapability
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class RemoteViewModel @Inject constructor(
    private val session: RemoteSession,
) : ViewModel() {

    /** The UI gates controls on this — never render a button the transport cannot serve. */
    val capabilities: StateFlow<Set<TransportCapability>> = session.capabilities

    val connection = session.state

    fun onKeyTap(key: RemoteKey) = viewModelScope.launch {
        session.press(key, KeyAction.TAP)
    }

    /**
     * Held keys must be released even if the composition dies mid-press, so PRESS/RELEASE
     * are driven from here rather than from a Composable's gesture scope.
     */
    fun onKeyPress(key: RemoteKey) = viewModelScope.launch { session.press(key, KeyAction.PRESS) }
    fun onKeyRelease(key: RemoteKey) = viewModelScope.launch { session.press(key, KeyAction.RELEASE) }
}
