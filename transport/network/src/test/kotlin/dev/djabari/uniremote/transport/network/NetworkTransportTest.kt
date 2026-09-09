package dev.djabari.uniremote.transport.network

import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportError
import dev.djabari.uniremote.transport.TransportState
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NetworkTransportTest {

    private class FakeAdapter(
        override val brand: TvBrand,
        private val identifyResult: Boolean = false,
        private val connectResult: Result<Unit> = Result.success(Unit),
    ) : BrandAdapter {
        var identifyCalls = 0
        var connectCalls = 0

        override val capabilities: Set<TransportCapability> =
            setOf(TransportCapability.DPAD, TransportCapability.POWER_ON)

        override suspend fun identify(target: RemoteTarget): Boolean {
            identifyCalls++
            return identifyResult
        }

        override suspend fun discover(): List<RemoteTarget> = emptyList()

        override suspend fun connect(target: RemoteTarget): Result<Unit> {
            connectCalls++
            return connectResult
        }

        override suspend fun send(key: RemoteKey): Result<Unit> = Result.success(Unit)
        override suspend fun sendText(text: String): Result<Unit> = Result.success(Unit)
        override suspend fun wakeOnLan(mac: String): Result<Unit> = Result.success(Unit)
        override suspend fun disconnect() = Unit
    }

    private fun target(
        brand: TvBrand = TvBrand.ROKU,
        ip: String? = "192.168.1.50",
        mac: String? = null,
    ) = RemoteTarget(
        id = "tv",
        displayName = "TV",
        brand = brand,
        ipAddress = ip,
        macAddress = mac,
    )

    @Test
    fun `supports an unlabelled target when it can identify by IP or wake by MAC`() {
        val roku = FakeAdapter(TvBrand.ROKU, identifyResult = true)
        val tizen = FakeAdapter(TvBrand.SAMSUNG_TIZEN)
        val transport = NetworkTransport(listOf(roku, tizen))

        assertThat(transport.supports(target(brand = TvBrand.GENERIC, ip = "192.168.1.50"))).isTrue()
        assertThat(transport.supports(target(brand = TvBrand.UNKNOWN, ip = "192.168.1.50"))).isTrue()
        assertThat(transport.supports(target(brand = TvBrand.GENERIC, ip = null, mac = "AA:BB:CC:DD:EE:FF"))).isTrue()
        assertThat(transport.supports(target(brand = TvBrand.GENERIC, ip = null))).isFalse()
        assertThat(transport.supports(target(brand = TvBrand.ROKU))).isTrue()
        assertThat(transport.supports(target(brand = TvBrand.ROKU, ip = null, mac = "AA:BB:CC:DD:EE:FF"))).isTrue()
        assertThat(transport.supports(target(brand = TvBrand.UNKNOWN, ip = null, mac = "AA:BB:CC:DD:EE:FF"))).isTrue()
        assertThat(transport.supports(target(brand = TvBrand.UNKNOWN, ip = null))).isFalse()
    }

    @Test
    fun `a labelled target connects to its own adapter without running identification`() = runTest {
        val roku = FakeAdapter(TvBrand.ROKU, identifyResult = true)
        val tizen = FakeAdapter(TvBrand.SAMSUNG_TIZEN)
        val transport = NetworkTransport(listOf(roku, tizen))
        val samsungTarget = target(brand = TvBrand.SAMSUNG_TIZEN)

        val result = transport.connect(samsungTarget)

        assertThat(result.isSuccess).isTrue()
        assertThat(tizen.connectCalls).isEqualTo(1)
        assertThat(roku.connectCalls).isEqualTo(0)
        assertThat(roku.identifyCalls).isEqualTo(0)
        assertThat(transport.resolvedTarget).isNull()
        assertThat(transport.state.value).isInstanceOf(TransportState.Connected::class.java)
    }

    @Test
    fun `a GENERIC target is routed through the single adapter that identifies it silently`() = runTest {
        val roku = FakeAdapter(TvBrand.ROKU, identifyResult = true)
        val tizen = FakeAdapter(TvBrand.SAMSUNG_TIZEN)
        val transport = NetworkTransport(listOf(roku, tizen))

        val result = transport.connect(target(brand = TvBrand.GENERIC))

        assertThat(result.isSuccess).isTrue()
        assertThat(roku.identifyCalls).isEqualTo(1)
        assertThat(roku.connectCalls).isEqualTo(1)
        // firstOrNull short-circuits: once Roku answers, Tizen is never consulted.
        assertThat(tizen.identifyCalls).isEqualTo(0)
        // The prompting adapters must never open a connection for an unlabelled target.
        assertThat(tizen.connectCalls).isEqualTo(0)
        assertThat(transport.resolvedTarget?.brand).isEqualTo(TvBrand.ROKU)
        assertThat(transport.capabilities.value)
            .containsExactlyElementsIn(setOf(TransportCapability.DPAD, TransportCapability.POWER_ON))
    }

    @Test
    fun `a GENERIC target no adapter can identify fails without connecting anywhere`() = runTest {
        val roku = FakeAdapter(TvBrand.ROKU)
        val tizen = FakeAdapter(TvBrand.SAMSUNG_TIZEN)
        val transport = NetworkTransport(listOf(roku, tizen))

        val result = transport.connect(target(brand = TvBrand.GENERIC))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("brand")
        assertThat(roku.connectCalls).isEqualTo(0)
        assertThat(tizen.connectCalls).isEqualTo(0)
        val state = transport.state.value as TransportState.Failed
        assertThat(state.reason).isEqualTo(TransportError.UNSUPPORTED_TARGET)
    }

    @Test
    fun `a MAC-only GENERIC target connects as a WoL-only target`() = runTest {
        val roku = FakeAdapter(TvBrand.ROKU, identifyResult = true)
        var sentMac: String? = null
        val transport = NetworkTransport(
            adapters = listOf(roku),
            sendWakeOnLan = { mac ->
                sentMac = mac
                Result.success(Unit)
            },
        )

        val result = transport.connect(target(brand = TvBrand.GENERIC, ip = null, mac = "AA:BB:CC:DD:EE:FF"))

        assertThat(result.isSuccess).isTrue()
        assertThat(roku.identifyCalls).isEqualTo(0)
        assertThat(roku.connectCalls).isEqualTo(0)
        assertThat(transport.capabilities.value).containsExactly(TransportCapability.POWER_ON)

        val wakeResult = transport.sendKey(RemoteKey.POWER_ON)

        assertThat(wakeResult.isSuccess).isTrue()
        assertThat(sentMac).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `a MAC-less GENERIC target without an IP cannot be identified`() = runTest {
        val roku = FakeAdapter(TvBrand.ROKU, identifyResult = true)
        val transport = NetworkTransport(listOf(roku))

        val result = transport.connect(target(brand = TvBrand.GENERIC, ip = null))

        assertThat(result.isFailure).isTrue()
        assertThat(roku.identifyCalls).isEqualTo(0)
        assertThat(roku.connectCalls).isEqualTo(0)
        assertThat((transport.state.value as TransportState.Failed).reason)
            .isEqualTo(TransportError.UNSUPPORTED_TARGET)
    }
}
