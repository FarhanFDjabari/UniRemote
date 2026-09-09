package dev.djabari.uniremote.transport.network

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Consumer TVs terminate TLS with a self-signed certificate generated on the device, so a
 * normal trust chain can never validate. Samsung's remote-control endpoint is wss-only on
 * current firmware, which leaves exactly two options: trust the TV's certificate without a
 * chain, or drop the feature.
 *
 * The relaxed client is therefore built here, in one place, and is used only for the
 * user's own TV on the local network. Everything else uses the strict client.
 */
object LanHttpClients {

    fun strict(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived websockets
        .build()

    /** Accepts the TV's self-signed certificate. Never use this for internet traffic. */
    fun selfSignedTolerant(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private const val CONNECT_TIMEOUT_SECONDS = 5L
}
