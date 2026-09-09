package dev.djabari.uniremote.transport.network.discovery

import dev.djabari.uniremote.model.RemoteTarget
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** One scan across every discovery mechanism, de-duplicated by address. */
@Singleton
class NetworkDiscovery @Inject constructor(
    private val mdns: MdnsDiscovery,
) {
    suspend fun scan(): List<RemoteTarget> = coroutineScope {
        val ssdp = async { SsdpDiscovery.search() }
        val androidTv = async { mdns.discoverAndroidTv() }
        (androidTv.await() + ssdp.await()).distinctBy { it.id }
    }
}
