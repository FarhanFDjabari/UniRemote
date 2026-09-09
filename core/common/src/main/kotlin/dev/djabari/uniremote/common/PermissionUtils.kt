package dev.djabari.uniremote.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {

    /** Permissions required for Bluetooth HID operations depending on API level. */
    fun getRequiredBluetoothPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    /** Runtime permissions requested by the app, including the optional notification prompt. */
    fun getRequiredRuntimePermissions(): List<String> = buildList {
        addAll(getRequiredBluetoothPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun hasPermissions(context: Context, permissions: List<String>): Boolean =
        permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

    fun hasBluetoothPermissions(context: Context): Boolean =
        hasPermissions(context, getRequiredBluetoothPermissions())
}
