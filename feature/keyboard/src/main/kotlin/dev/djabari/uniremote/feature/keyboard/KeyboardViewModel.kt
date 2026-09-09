package dev.djabari.uniremote.feature.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.djabari.uniremote.model.KeyAction
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.session.RemoteSession
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyboardViewModel @Inject constructor(
    private val session: RemoteSession,
) : ViewModel() {

    val connection: StateFlow<TransportState> = session.state
    val capabilities: StateFlow<Set<TransportCapability>> = session.capabilities

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun sendText(text: String) = viewModelScope.launch {
        _error.value = null
        val result = session.type(text)
        if (result.isFailure) {
            _error.value = result.exceptionOrNull()?.message ?: "Failed to send text"
        }
    }

    fun sendKey(key: RemoteKey) = viewModelScope.launch {
        _error.value = null
        val result = session.press(key, KeyAction.TAP)
        if (result.isFailure) {
            _error.value = result.exceptionOrNull()?.message ?: "Failed to send key"
        }
    }

    fun clearError() {
        _error.value = null
    }
}
