package dev.djabari.uniremote.common

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionUtilsTest {

    @Test
    fun `Bluetooth permissions do not include notifications`() {
        assertThat(PermissionUtils.getRequiredBluetoothPermissions())
            .containsExactly(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
            .inOrder()
    }

    @Test
    fun `runtime permission request includes notifications separately`() {
        assertThat(PermissionUtils.getRequiredRuntimePermissions())
            .containsExactly(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            .inOrder()
    }

    @Test
    fun `notification denial does not block Bluetooth capability`() {
        val context = PermissionContext(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )

        assertThat(PermissionUtils.hasBluetoothPermissions(context)).isTrue()
    }

    @Test
    fun `missing Bluetooth permission blocks Bluetooth capability`() {
        val context = PermissionContext(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )

        assertThat(PermissionUtils.hasBluetoothPermissions(context)).isFalse()
    }

    private class PermissionContext(vararg grantedPermissions: String) : ContextWrapper(null) {
        private val grantedPermissions = grantedPermissions.toSet()

        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (permission in grantedPermissions) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            }
    }
}
