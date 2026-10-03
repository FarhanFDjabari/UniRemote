package dev.djabari.uniremote.transport.bthid

/**
 * System Bluetooth events the HID transport cannot observe itself, since it has no Context.
 * The session layer listens for the broadcasts and forwards them here.
 */
interface BluetoothLinkEvents {
    /** Bluetooth was switched off: the framework dropped the registration and any link with it. */
    fun onBluetoothDisabled()

    /** The bond with [address] was removed; connecting to it can only fail until the user pairs again. */
    fun onBondRemoved(address: String)

    /** The ACL link to [address] dropped. Some stacks report this without a HID disconnect callback. */
    fun onAclDisconnected(address: String)
}
