package com.autodj.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.autodj.R
import com.autodj.ui.MainActivity

/**
 * Runs the Chromecast stream ([MixStreamer]) while a cast is on. Capturing the app's audio needs
 * a media projection, and Android only hands one to a foreground service of this type.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class CastStreamService : Service() {
    private var projection: MediaProjection? = null
    private var streamer: MixStreamer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val result = intent?.getIntExtra(EXTRA_RESULT, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(intent?.getStringExtra(EXTRA_DEVICE).orEmpty()), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        // A new capture replaces any running one; two would fight over the same audio.
        release()
        Log.i(TAG, "cast stream service: starting the capture")
        try {
            val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(result, data)
            // Android 14 wants a callback registered before the projection is used.
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "cast stream service: Android ended the capture permission")
                    if (projection === p) stopSelf()
                }
            }, Handler(Looper.getMainLooper()))
            projection = p
            val s = MixStreamer(this) { reason ->
                Handler(Looper.getMainLooper()).post { CastControl.onStreamFailed(reason); stopSelf() }
            }.also { streamer = it }
            CastControl.onStreamReady(s, s.start(p))
        } catch (e: Exception) {
            Log.w(TAG, "couldn't start the cast stream: ${e.message}")
            CastControl.onStreamFailed(e.message ?: e.javaClass.simpleName)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "cast stream service: stopped")
        release()
        CastControl.onStreamStopped()
        super.onDestroy()
    }

    private fun release() {
        streamer?.stop()
        streamer = null
        val p = projection
        projection = null
        p?.stop()
    }

    private fun notification(device: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Casting", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, CastStreamService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Casting the mix")
            .setContentText(device.ifBlank { "To your TV" })
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop casting", stop).build())
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AutoDJ"
        private const val CHANNEL = "cast"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_STOP = "stop"
        private const val EXTRA_RESULT = "result"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_DEVICE = "device"

        fun start(context: Context, resultCode: Int, data: Intent, device: String) {
            context.startForegroundService(
                Intent(context, CastStreamService::class.java)
                    .putExtra(EXTRA_RESULT, resultCode).putExtra(EXTRA_DATA, data).putExtra(EXTRA_DEVICE, device),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CastStreamService::class.java))
        }
    }
}
