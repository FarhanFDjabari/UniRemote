package dev.djabari.uniremote.transport.network.discovery

import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.model.TvBrand
import org.junit.Test

class SsdpDiscoveryTest {

    private val rokuResponse = """
        HTTP/1.1 200 OK
        Cache-Control: max-age=3600
        ST: roku:ecp
        USN: uuid:roku:ecp:1GU48T017973
        LOCATION: http://192.168.1.134:8060/
    """.trimIndent()

    @Test
    fun `parses the device address out of the LOCATION header`() {
        val target = SsdpDiscovery.parseTarget(rokuResponse, TvBrand.ROKU)

        assertThat(target).isNotNull()
        assertThat(target!!.ipAddress).isEqualTo("192.168.1.134")
        assertThat(target.brand).isEqualTo(TvBrand.ROKU)
        assertThat(target.id).isEqualTo("192.168.1.134")
    }

    @Test
    fun `returns null when the response carries no LOCATION`() {
        val response = "HTTP/1.1 200 OK\nST: roku:ecp"

        assertThat(SsdpDiscovery.parseTarget(response, TvBrand.ROKU)).isNull()
    }

    @Test
    fun `searches one target per supported ecosystem`() {
        assertThat(SsdpDiscovery.SEARCH_TARGETS.values)
            .containsExactly(TvBrand.ROKU, TvBrand.SAMSUNG_TIZEN, TvBrand.LG_WEBOS)
    }
}
