package dev.djabari.uniremote.transport.network.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Wake-on-LAN helper.
 *
 * Sends a magic packet (6 bytes of 0xFF followed by 16 repetitions of the target's MAC address)
 * over UDP broadcast to port 9 and port 7.
 */
object WakeOnLan {

    fun buildMagicPacket(macAddress: String): ByteArray {
        val macBytes = parseMac(macAddress)
        val packet = ByteArray(6 + 16 * macBytes.size)
        // 6 bytes of 0xFF
        for (i in 0 until 6) {
            packet[i] = 0xFF.toByte()
        }
        // 16 repetitions of MAC address
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packet, 6 + i * macBytes.size, macBytes.size)
        }
        return packet
    }

    private fun parseMac(macStr: String): ByteArray {
        val clean = macStr.replace(":", "").replace("-", "")
        require(clean.length == 12) { "Invalid MAC address format: $macStr" }
        val bytes = ByteArray(6)
        for (i in 0 until 6) {
            bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return bytes
    }

    suspend fun send(
        macAddress: String,
        broadcastIp: String = "255.255.255.255",
        ports: List<Int> = listOf(9, 7),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val packetData = buildMagicPacket(macAddress)
            val address = InetAddress.getByName(broadcastIp)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (port in ports) {
                    val packet = DatagramPacket(packetData, packetData.size, address, port)
                    socket.send(packet)
                }
            }
        }
    }
}
