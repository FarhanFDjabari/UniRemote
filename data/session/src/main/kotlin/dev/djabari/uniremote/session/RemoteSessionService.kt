package dev.djabari.uniremote.session

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.djabari.uniremote.common.PermissionUtils
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.transport.bthid.BluetoothLinkEvents
import dev.djabari.uniremote.transport.userMessage
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
    lateinit var profileManager: HidProfileController

    @Inject
    lateinit var linkEvents: BluetoothLinkEvents

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateObserverJob: Job? = null
    private var eventReceiver: BluetoothEventReceiver? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: RemoteSessionService get() = this@RemoteSessionService
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
        profileManager.open()
        val router = BluetoothEventRouter(linkEvents, profileManager, session, serviceScope)
        eventReceiver = BluetoothEventReceiver(router).also { receiver ->
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
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
            is TransportState.Reconnecting -> "Connection lost. Reconnecting to ${state.target.displayName} (${state.attempt}/${state.maxAttempts})..."
            is TransportState.AwaitingHost -> "Waiting for TV to connect..."
            is TransportState.Preparing -> "Preparing Bluetooth HID..."
            is TransportState.Failed -> state.reason.userMessage
            is TransportState.Idle -> "Ready to connect"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UniRemote")
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
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
        eventReceiver?.let { unregisterReceiver(it) }
        eventReceiver = null

        // Teardown has to finish before the process lets go of the profile proxy: a leaked
        // HID registration cannot be reclaimed until the phone reboots. Launching it on a
        // scope that is cancelled on the next line meant it never ran at all.
        if (PermissionUtils.hasBluetoothPermissions(this)) {
            runBlocking { withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) { session.disconnect() } }
        }
        serviceScope.cancel()

        profileManager.close()

        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "uniremote_session"
        const val ACTION_DISCONNECT = "dev.djabari.uniremote.action.DISCONNECT"
        private const val DISCONNECT_TIMEOUT_MS = 2_000L
    }
}
