package dev.djabari.uniremote.session

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import dev.djabari.uniremote.common.PermissionUtils
import dev.djabari.uniremote.transport.bthid.RealHidDeviceProxy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps profile proxy ownership and reopening in one place for reset and Bluetooth-on recovery.
 */
interface HidProfileController {
    fun open()
    fun close()
    suspend fun reopen()
}

@Singleton
class BluetoothHidProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adapter: BluetoothAdapter?,
    private val hidProxy: RealHidDeviceProxy,
) : HidProfileController {

    private var hidProfile: BluetoothHidDevice? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE && proxy is BluetoothHidDevice) {
                hidProfile = proxy
                hidProxy.setHidDevice(proxy)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidProfile = null
                hidProxy.setHidDevice(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun open() {
        if (PermissionUtils.hasBluetoothPermissions(context)) {
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        val proxy = hidProfile
        // Only closeProfileProxy touches the Bluetooth stack, so only it needs the guard.
        // Releasing the references is a local write, and skipping it when the permission had
        // been revoked left the process holding a HID device it could no longer use.
        if (proxy != null) {
            if (PermissionUtils.hasBluetoothPermissions(context)) {
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
            }
            hidProfile = null
        }
        hidProxy.setHidDevice(null)
    }

    override suspend fun reopen() {
        close()
        open()
        // open() delivers asynchronously; a connect issued right after Bluetooth returns
        // would otherwise find no proxy and be reported as a broken Bluetooth stack.
        hidProxy.awaitReady()
    }
}
