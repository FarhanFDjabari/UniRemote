package dev.djabari.uniremote.session

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.transport.network.discovery.NetworkDiscovery
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every way the app can find a TV, behind one surface so feature modules never touch a
 * Bluetooth adapter or a discovery protocol directly.
 */
@Singleton
class DeviceDiscovery @Inject constructor(
    private val networkDiscovery: NetworkDiscovery,
    private val bluetoothAdapter: BluetoothAdapter?,
) {

    /**
     * TVs already bonded with the phone. Bonded devices are the ones BT HID can reach, so
     * this is the primary list; scanning is for the network fallback.
     */
    @SuppressLint("MissingPermission")
    fun bondedTargets(): List<RemoteTarget> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty().map { device ->
                RemoteTarget(
                    id = device.address,
                    displayName = device.name ?: "TV (${device.address})",
                    brand = brandOf(device.name),
                    bluetoothAddress = device.address,
                )
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    suspend fun scanNetwork(): List<RemoteTarget> = networkDiscovery.scan()

    private fun brandOf(name: String?): TvBrand {
        val lower = name?.lowercase() ?: return TvBrand.GENERIC
        return when {
            "roku" in lower -> TvBrand.ROKU
            "samsung" in lower || "tizen" in lower -> TvBrand.SAMSUNG_TIZEN
            "lg" in lower || "webos" in lower -> TvBrand.LG_WEBOS
            "fire" in lower -> TvBrand.FIRE_TV
            listOf("sony", "bravia", "android tv", "google tv", "chromecast", "shield").any { it in lower } ->
                TvBrand.ANDROID_TV
            else -> TvBrand.GENERIC
        }
    }
}
