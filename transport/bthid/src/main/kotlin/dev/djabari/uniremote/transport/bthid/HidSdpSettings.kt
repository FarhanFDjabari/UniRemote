package dev.djabari.uniremote.transport.bthid

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings

object HidSdpSettings {
    const val NAME = "UniRemote"
    const val DESCRIPTION = "Android Bluetooth HID Remote"
    const val PROVIDER = "UniRemote"
    val SUBCLASS = BluetoothHidDevice.SUBCLASS1_COMBO

    fun createSdpSettings(): BluetoothHidDeviceAppSdpSettings =
        BluetoothHidDeviceAppSdpSettings(
            NAME,
            DESCRIPTION,
            PROVIDER,
            SUBCLASS,
            HidDescriptor.BYTES,
        )

    fun createQosSettings(): BluetoothHidDeviceAppQosSettings =
        BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX,
        )
}
