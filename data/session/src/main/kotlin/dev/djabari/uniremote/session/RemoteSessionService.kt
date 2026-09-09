package dev.djabari.uniremote.session

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.djabari.uniremote.common.PermissionUtils
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.transport.bthid.RealHidDeviceProxy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Foreground service (type `connectedDevice`) that holds the Bluetooth HID Device registration
 * and active session across Activity lifecycle transitions.
 */
@AndroidEntryPoint
class RemoteSessionService : Service() {

    @Inject
    lateinit var session: RemoteSession

    @Inject
    lateinit var hidProxy: RealHidDeviceProxy

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateObserverJob: Job? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidProfile: BluetoothHidDevice? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: RemoteSessionService get() = this@RemoteSessionService
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE && proxy is BluetoothHidDevice) {
                hidProfile = proxy
                hidProxy.setHidDevice(proxy)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidProfile = null
                hidProxy.setHidDevice(null)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!PermissionUtils.hasBluetoothPermissions(this)) {
            // A sticky restart can occur after Nearby Devices was revoked and bypass the
            // activity's permission flow. Do not touch Bluetooth or promote to an FGS.
            stopSelf()
            return
        }
        createNotificationChannel()
        initBluetoothHidProfile()
        observeSessionState()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!PermissionUtils.hasBluetoothPermissions(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_DISCONNECT) {
            serviceScope.launch {
                session.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val initialNotif = buildNotification(session.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun initBluetoothHidProfile() {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        bluetoothAdapter?.getProfileProxy(applicationContext, profileListener, BluetoothProfile.HID_DEVICE)
    }

    private fun observeSessionState() {
        stateObserverJob = serviceScope.launch {
            session.state.collect { state ->
                val notification = buildNotification(state)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(state: TransportState): Notification {
        val disconnectIntent = Intent(this, RemoteSessionService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            0,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val statusText = when (state) {
            is TransportState.Connected -> "Connected to ${state.target.displayName}"
            is TransportState.Connecting -> "Connecting to ${state.target.displayName}..."
            is TransportState.AwaitingHost -> "Waiting for TV to connect..."
            is TransportState.Preparing -> "Preparing Bluetooth HID..."
            is TransportState.Failed -> "Connection failed: ${state.reason}"
            is TransportState.Idle -> "Ready to connect"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UniRemote")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(state is TransportState.Connected || state is TransportState.Connecting)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectPendingIntent,
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "UniRemote Session",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows active connection status to your TV"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        stateObserverJob?.cancel()

        // Teardown has to finish before the process lets go of the profile proxy: a leaked
        // HID registration cannot be reclaimed until the phone reboots. Launching it on a
        // scope that is cancelled on the next line meant it never ran at all.
        if (PermissionUtils.hasBluetoothPermissions(this)) {
            runBlocking { withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) { session.disconnect() } }
        }
        serviceScope.cancel()

        // Only closeProfileProxy touches the Bluetooth stack, so only it needs the guard.
        // Releasing the references is a local write, and skipping it when the permission had
        // been revoked left the process holding a HID device it could no longer use.
        hidProfile?.let { proxy ->
            if (PermissionUtils.hasBluetoothPermissions(this)) {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
            }
            hidProfile = null
        }
        hidProxy.setHidDevice(null)

        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "uniremote_session"
        const val ACTION_DISCONNECT = "dev.djabari.uniremote.action.DISCONNECT"
        private const val DISCONNECT_TIMEOUT_MS = 2_000L
    }
}
