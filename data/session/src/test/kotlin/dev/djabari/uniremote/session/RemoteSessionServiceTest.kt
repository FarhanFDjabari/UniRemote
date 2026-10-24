package dev.djabari.uniremote.session

import android.app.Service
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import dev.djabari.uniremote.common.PermissionUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    @Test
    fun `quit stops the service without asking for a sticky restart`() {
        val service = Robolectric.buildService(RemoteSessionService::class.java).get()
        val quit = Intent(service, RemoteSessionService::class.java).apply {
            action = RemoteSessionService.ACTION_QUIT
        }

        assertThat(service.onStartCommand(quit, 0, 1)).isEqualTo(Service.START_NOT_STICKY)
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }
}
