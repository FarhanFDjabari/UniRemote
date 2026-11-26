package dev.djabari.uniremote.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.session.DeviceDiscovery
import dev.djabari.uniremote.session.RemoteSession
import dev.djabari.uniremote.session.TargetRepository
import dev.djabari.uniremote.transport.TransportState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val session: RemoteSession,
    private val repository: TargetRepository,
    private val discovery: DeviceDiscovery,
) : ViewModel() {

    val connection: StateFlow<TransportState> = session.state
    val activeTarget: StateFlow<RemoteTarget?> = session.activeTarget

    val savedTargets: StateFlow<List<RemoteTarget>> = repository.savedTargets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pairedBluetoothDevices = MutableStateFlow<List<RemoteTarget>>(emptyList())
    val pairedBluetoothDevices: StateFlow<List<RemoteTarget>> = _pairedBluetoothDevices.asStateFlow()

    init {
        refreshBluetoothDevices()
    }

    fun refreshBluetoothDevices() {
        _pairedBluetoothDevices.value = discovery.bondedTargets()
    }

    fun connect(target: RemoteTarget) = viewModelScope.launch {
        session.connect(target)
    }

    /** Reconnects to the active target, or falls back to the session's last-known target. */
    fun retry() = viewModelScope.launch {
        session.activeTarget.value?.let { session.connect(it) } ?: session.reconnect()
    }

    fun disconnect() = viewModelScope.launch {
        session.disconnect()
    }

    /** Tears the Bluetooth stack down and reconnects from scratch, like a force-stop. */
    fun resetConnection() = viewModelScope.launch {
        session.resetConnection()
    }

    fun removeTarget(targetId: String) = viewModelScope.launch {
        repository.removeTarget(targetId)
    }
}
