package dev.djabari.uniremote.transport.network.adapters

import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.network.BrandAdapter
import dev.djabari.uniremote.transport.network.wol.WakeOnLan

/**
 * Android TV / Google TV over the network.
 *
 * Key traffic is deliberately *not* implemented here: the androidtvremote2 protocol needs a
 * TLS client certificate exchanged through an on-screen six-digit pairing flow, and Android
 * TV is precisely the ecosystem the Bluetooth HID transport serves best — so duplicating it
 * over the network buys nothing except the one thing HID physically cannot do, which is
 * waking a TV whose radio is off.
 *
 * This adapter therefore claims exactly one capability, [TransportCapability.POWER_ON], and
 * refuses everything else instead of silently accepting keys that never arrive.
 */
class AndroidTvAdapter : BrandAdapter {

    override val brand = TvBrand.ANDROID_TV

    override val capabilities: Set<TransportCapability> = setOf(TransportCapability.POWER_ON)

    override suspend fun discover(): List<RemoteTarget> = emptyList()

    override suspend fun connect(target: RemoteTarget): Result<Unit> =
        if (target.macAddress != null) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalArgumentException(
                    "Android TV over the network only supports Wake-on-LAN, which needs the TV's MAC address. Use Bluetooth for everything else.",
                ),
            )
        }

    override suspend fun send(key: RemoteKey): Result<Unit> = Result.failure(
        UnsupportedOperationException("Android TV network control is not implemented; use the Bluetooth HID transport"),
    )

    override suspend fun sendText(text: String): Result<Unit> = Result.failure(
        UnsupportedOperationException("Android TV network text input is not implemented; use the Bluetooth HID transport"),
    )

    override suspend fun wakeOnLan(mac: String): Result<Unit> = WakeOnLan.send(mac)

    override suspend fun disconnect() = Unit
}
