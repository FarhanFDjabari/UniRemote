package dev.djabari.uniremote.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
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

    private val _discoveredTargets = MutableStateFlow<List<RemoteTarget>>(emptyList())
    val discoveredTargets: StateFlow<List<RemoteTarget>> = _discoveredTargets.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        refreshBluetoothDevices()
    }

    fun refreshBluetoothDevices() {
        _pairedBluetoothDevices.value = discovery.bondedTargets()
    }

    /** Finds TVs reachable over the network — the fallback path for Roku and cold power-on. */
    fun scanNetwork() = viewModelScope.launch {
        _isScanning.value = true
        _discoveredTargets.value = discovery.scanNetwork()
        _isScanning.value = false
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

    fun addManualTarget(
        name: String,
        brand: TvBrand,
        ipAddress: String?,
        macAddress: String?,
        bluetoothAddress: String?,
    ) = viewModelScope.launch {
        val target = RemoteTarget(
            id = ipAddress ?: bluetoothAddress ?: macAddress ?: System.currentTimeMillis().toString(),
            displayName = name.ifBlank { "Smart TV (${brand.name})" },
            brand = brand,
            ipAddress = ipAddress?.ifBlank { null },
            macAddress = macAddress?.ifBlank { null },
            bluetoothAddress = bluetoothAddress?.ifBlank { null },
        )
        repository.saveTarget(target)
        session.connect(target)
    }

    fun removeTarget(targetId: String) = viewModelScope.launch {
        repository.removeTarget(targetId)
    }
}
