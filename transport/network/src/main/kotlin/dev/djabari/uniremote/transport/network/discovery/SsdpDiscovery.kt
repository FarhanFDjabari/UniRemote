package dev.djabari.uniremote.transport.network.discovery

import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * SSDP M-SEARCH discovery.
 *
 * Each ecosystem answers a different search target, so the search target itself identifies
 * the brand — no heuristics on device names, which users rename anyway.
 */
object SsdpDiscovery {

    private const val MULTICAST_ADDRESS = "239.255.255.250"
    private const val MULTICAST_PORT = 1900
    private const val MX_SECONDS = 2

    /** Search target -> the brand that answers it. */
    val SEARCH_TARGETS: Map<String, TvBrand> = mapOf(
        "roku:ecp" to TvBrand.ROKU,
        "urn:samsung.com:device:RemoteControlReceiver:1" to TvBrand.SAMSUNG_TIZEN,
        "urn:lge-com:service:webos-second-screen:1" to TvBrand.LG_WEBOS,
    )

    /**
     * Extracts the device address from an SSDP response. Returns null when the response
     * carries no usable LOCATION header — some devices answer with only a NOTIFY body.
     */
    fun parseTarget(response: String, brand: TvBrand): RemoteTarget? {
        val location = response.lineSequence()
            .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
            ?.substringAfter(':', "")
            ?.trim()
            ?: return null

        val host = Regex("https?://([^/:]+)").find(location)?.groupValues?.get(1) ?: return null

        return RemoteTarget(
            id = host,
            displayName = "${brand.displayLabel} ($host)",
            brand = brand,
            ipAddress = host,
        )
    }

    suspend fun search(timeoutMs: Int = 3_000): List<RemoteTarget> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, RemoteTarget>()

        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMs
                val group = InetAddress.getByName(MULTICAST_ADDRESS)

                SEARCH_TARGETS.keys.forEach { searchTarget ->
                    val message = buildString {
                        append("M-SEARCH * HTTP/1.1\r\n")
                        append("HOST: $MULTICAST_ADDRESS:$MULTICAST_PORT\r\n")
                        append("MAN: \"ssdp:discover\"\r\n")
                        append("ST: $searchTarget\r\n")
                        append("MX: $MX_SECONDS\r\n\r\n")
                    }.toByteArray()
                    socket.send(DatagramPacket(message, message.size, group, MULTICAST_PORT))
                }

                val buffer = ByteArray(2048)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    val response = String(packet.data, 0, packet.length)
                    val brand = SEARCH_TARGETS.entries
                        .firstOrNull { (st, _) -> response.contains(st, ignoreCase = true) }
                        ?.value
                        ?: continue
                    parseTarget(response, brand)?.let { found[it.id] = it }
                }
            }
        }

        found.values.toList()
    }
}

private val TvBrand.displayLabel: String
    get() = when (this) {
        TvBrand.ANDROID_TV -> "Android TV"
        TvBrand.FIRE_TV -> "Fire TV"
        TvBrand.SAMSUNG_TIZEN -> "Samsung TV"
        TvBrand.LG_WEBOS -> "LG TV"
        TvBrand.ROKU -> "Roku"
        TvBrand.GENERIC, TvBrand.UNKNOWN -> "TV"
    }
