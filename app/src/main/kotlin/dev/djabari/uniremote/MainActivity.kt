package dev.djabari.uniremote

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.djabari.uniremote.common.PermissionUtils
import dev.djabari.uniremote.feature.keyboard.KeyboardViewModel
import dev.djabari.uniremote.feature.pairing.PairingViewModel
import dev.djabari.uniremote.feature.remote.RemoteViewModel
import dev.djabari.uniremote.feature.touchpad.TouchpadViewModel
import dev.djabari.uniremote.session.RemoteSession
import dev.djabari.uniremote.session.RemoteSessionService
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var session: RemoteSession

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Android validates connectedDevice FGS prerequisites at startForeground(). Do not
        // start the service until the Bluetooth runtime permissions have been granted.
        if (PermissionUtils.hasBluetoothPermissions(this)) {
            startSessionService()
        }

        setContent {
            val permissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                if (PermissionUtils.hasBluetoothPermissions(this@MainActivity)) {
                    startSessionService()
                    reconnectSession()
                }
            }

            LaunchedEffect(Unit) {
                val needed = PermissionUtils.getRequiredRuntimePermissions()
                if (!PermissionUtils.hasPermissions(this@MainActivity, needed)) {
                    permissionsLauncher.launch(needed.toTypedArray())
                }
            }

            val remoteViewModel: RemoteViewModel = hiltViewModel()
            val touchpadViewModel: TouchpadViewModel = hiltViewModel()
            val keyboardViewModel: KeyboardViewModel = hiltViewModel()
            val pairingViewModel: PairingViewModel = hiltViewModel()

            UniRemoteApp(
                remoteViewModel = remoteViewModel,
                touchpadViewModel = touchpadViewModel,
                keyboardViewModel = keyboardViewModel,
                pairingViewModel = pairingViewModel,
            )
        }
    }

    /**
     * TVs drop idle HID links; users come back from a video app expecting the remote to
     * still work. Silently re-establish the last session instead of making them re-pair.
     */
    override fun onResume() {
        super.onResume()
        if (PermissionUtils.hasBluetoothPermissions(this)) {
            reconnectSession()
        }
    }

    private fun reconnectSession() {
        lifecycleScope.launch { session.reconnect() }
    }

    private fun startSessionService() {
        val intent = Intent(this, RemoteSessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
