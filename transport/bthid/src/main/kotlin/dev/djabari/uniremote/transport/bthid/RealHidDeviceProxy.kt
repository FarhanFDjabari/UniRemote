package dev.djabari.uniremote.transport.bthid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Concrete implementation wrapping the Android framework's [BluetoothHidDevice].
 */
class RealHidDeviceProxy(
    hidDevice: BluetoothHidDevice? = null,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
) : HidDeviceProxy {

    private val profile = MutableStateFlow(hidDevice)

    private val hidDevice: BluetoothHidDevice? get() = profile.value

    fun setHidDevice(device: BluetoothHidDevice?) {
        profile.value = device
    }

    override suspend fun awaitReady(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { profile.first { it != null } } != null

    @SuppressLint("MissingPermission")
    override fun registerApp(callback: BluetoothHidDevice.Callback): Boolean {
        val hid = hidDevice ?: return false
        val sdp = HidSdpSettings.createSdpSettings()
        val qos = HidSdpSettings.createQosSettings()
        return hid.registerApp(sdp, null, qos, executor, callback)
    }

    @SuppressLint("MissingPermission")
    override fun unregisterApp(): Boolean {
        val hid = hidDevice ?: return false
        return hid.unregisterApp()
    }

    @SuppressLint("MissingPermission")
    override fun sendReport(device: BluetoothDevice?, id: Int, data: ByteArray): Boolean {
        val hid = hidDevice ?: return false
        return hid.sendReport(device, id, data)
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: BluetoothDevice): Boolean {
        val hid = hidDevice ?: return false
        return hid.connect(device)
    }

    @SuppressLint("MissingPermission")
    override fun disconnect(device: BluetoothDevice): Boolean {
        val hid = hidDevice ?: return false
        return hid.disconnect(device)
    }
}
