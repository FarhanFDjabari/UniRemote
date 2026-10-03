package dev.djabari.uniremote.session

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import dev.djabari.uniremote.transport.bthid.BluetoothLinkEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class BluetoothEventRouter(
    private val linkEvents: BluetoothLinkEvents,
    private val profile: HidProfileController,
    private val session: RemoteSession,
    private val scope: CoroutineScope,
) {
    fun onAdapterStateChanged(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> linkEvents.onBluetoothDisabled()
            BluetoothAdapter.STATE_ON -> scope.launch {
                profile.reopen()
                session.reconnect()
            }
        }
    }

    fun onBondStateChanged(address: String, bondState: Int) {
        if (bondState == BluetoothDevice.BOND_NONE) linkEvents.onBondRemoved(address)
    }

    fun onAclDisconnected(address: String) = linkEvents.onAclDisconnected(address)
}

class BluetoothEventReceiver(private val router: BluetoothEventRouter) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                if (intent.hasExtra(BluetoothAdapter.EXTRA_STATE)) {
                    router.onAdapterStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR))
                }
            }
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (device != null && intent.hasExtra(BluetoothDevice.EXTRA_BOND_STATE)) {
                    router.onBondStateChanged(device.address, intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR))
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (device != null) router.onAclDisconnected(device.address)
            }
        }
    }
}
