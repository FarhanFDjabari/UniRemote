package dev.djabari.uniremote.session

import android.app.Service
import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.common.PermissionUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteSessionServiceTest {

    @Test
    fun `restart without Bluetooth permission stops before foreground promotion`() {
        val service = Robolectric.buildService(RemoteSessionService::class.java).get()

        assertThat(PermissionUtils.hasBluetoothPermissions(service)).isFalse()
        assertThat(service.onStartCommand(null, 0, 1)).isEqualTo(Service.START_NOT_STICKY)
    }
}
