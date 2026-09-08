package dev.djabari.tvremote.transport.bthid

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice

/**
 * Thin seam over the final, unmockable framework class.
 *
 * `BluetoothHidDevice` cannot be faked in a JVM test, so every call into it goes through
 * this interface. Wrap it on day one — retrofitting the seam after the transport is written
 * is painful and you will end up with an untested state machine.
 */
interface HidDeviceProxy {
    fun registerApp(callback: BluetoothHidDevice.Callback): Boolean
    fun unregisterApp(): Boolean
    fun sendReport(device: BluetoothDevice?, id: Int, data: ByteArray): Boolean
    fun connect(device: BluetoothDevice): Boolean
    fun disconnect(device: BluetoothDevice): Boolean
}
