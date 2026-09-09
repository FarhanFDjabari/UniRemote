package dev.djabari.uniremote.transport.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * mDNS discovery for Android TV / Google TV, which advertises `_androidtvremote2._tcp`.
 *
 * Resolution is what yields an address, so every found service is resolved before it is
 * reported; unresolvable services are dropped rather than shown as unreachable entries.
 */
@Singleton
class MdnsDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun discoverAndroidTv(timeoutMs: Long = 4_000): List<RemoteTarget> {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return emptyList()
        val found = ConcurrentHashMap<String, RemoteTarget>()

        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String?) = Unit

                    override fun onServiceFound(service: NsdServiceInfo) {
                        manager.resolveService(
                            service,
                            object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) = Unit

                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    val host = info.host?.hostAddress ?: return
                                    found[host] = RemoteTarget(
                                        id = host,
                                        displayName = info.serviceName ?: "Android TV ($host)",
                                        brand = TvBrand.ANDROID_TV,
                                        ipAddress = host,
                                    )
                                }
                            },
                        )
                    }

                    override fun onServiceLost(service: NsdServiceInfo?) = Unit
                    override fun onDiscoveryStopped(serviceType: String?) = Unit

                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                        if (!continuation.isCompleted) continuation.resume(Unit)
                    }

                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
                }

                manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                continuation.invokeOnCancellation {
                    runCatching { manager.stopServiceDiscovery(listener) }
                }
            }
        }

        return found.values.toList()
    }

    private companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp"
    }
}
