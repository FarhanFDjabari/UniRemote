package dev.djabari.tvremote.session

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service (type `connectedDevice`) that owns the HID registration and the
 * live connection.
 *
 * It has to be a service, not an Activity-scoped object: users switch to a streaming app
 * mid-session and come back, and losing the HID registration on every backgrounding would
 * mean re-pairing. It also gives the connection the user-visible notification Android
 * requires for this FGS type.
 *
 * PHASE 1 SKELETON.
 */
class RemoteSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO(phase1): startForeground(NOTIF_ID, buildNotification(state)) BEFORE any
        //   Bluetooth work; then acquire the HID profile proxy and register.
        return START_STICKY
    }

    override fun onDestroy() {
        // TODO(phase1): release held keys, unregisterApp(), closeProfileProxy().
        super.onDestroy()
    }
}
