package dev.djabari.uniremote.model

/** A TV the user has saved, independent of how we currently reach it. */
data class RemoteTarget(
    val id: String,
    val displayName: String,
    val brand: TvBrand,
    val bluetoothAddress: String? = null,
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val preferredTransport: TransportId? = null,
)

enum class TvBrand {
    ANDROID_TV,
    FIRE_TV,
    SAMSUNG_TIZEN,
    LG_WEBOS,
    ROKU,
    GENERIC,
    UNKNOWN;

    /**
     * Whether this brand is known to accept a Bluetooth Classic HID peripheral.
     * Roku uses a proprietary BT profile and rejects generic HID hosts, so it is
     * network-only. Everything else gets an attempt.
     */
    val acceptsBluetoothHid: Boolean get() = this != ROKU
}

@JvmInline
value class TransportId(val value: String) {
    companion object {
        val BLUETOOTH_HID = TransportId("bt-hid")
        val NETWORK = TransportId("network")
    }
}
