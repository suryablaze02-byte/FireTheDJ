package com.autodj.analysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.autodj.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while [AnalysisJobs] has songs to get ready, with a quiet notification
 * of how far it has got, so analysis carries on with the screen off or the app swiped away.
 * It stops itself as soon as the queue is empty.
 */
@UnstableApi
class AnalysisService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Getting music ready", NotificationManager.IMPORTANCE_LOW))
        val first = notification(AnalysisJobs.progress.value)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, first)
        running = true
        startedAt = System.currentTimeMillis()
        val budget = BUDGET_MS - usedToday(this)
        // With the screen off the processor would sleep and analysis would stall.
        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FireTheDJ:analysis")
            .apply { acquire(budget.coerceAtLeast(60_000L)) }
        // Background analysis switched off in Settings: analysis carries on only while the app is open.
        scope.launch { AnalysisJobs.settings.collect { if (!it.background) stopSelf() } }
        scope.launch {
            AnalysisJobs.progress.collect { p ->
                // Out of today's background time: stop; analysis carries on while the app is open.
                if (!p.busy || System.currentTimeMillis() - startedAt > budget) {
                    stopSelf()
                } else {
                    nm.notify(ID, notification(p))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        running = false
        wake?.takeIf { it.isHeld }?.release()
        // Android 15 allows this kind of service six hours a day: count the time it ran today.
        val p = getSharedPreferences(PREFS, 0)
        val day = System.currentTimeMillis() / DAY_MS
        val used = if (p.getLong("day", -1) == day) p.getLong("used", 0) else 0
        p.edit().putLong("day", day).putLong("used", used + (System.currentTimeMillis() - startedAt)).apply()
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(p: AnalysisJobs.Progress): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val builder = Notification.Builder(this, CHANNEL)
        return builder
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Getting your music ready")
            .setContentText("Song ${minOf(p.done + 1, p.total)} of ${p.total}" + if (p.lyrics > 0) " · lyrics for ${p.lyrics} so far" else "")
            .setProgress(p.total, p.done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private var startedAt = 0L
    private var wake: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL = "analysis"
        private const val ID = 7
        private const val PREFS = "analysis_service"
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Under Android 15's six hours a day for this kind of service, with room to spare. */
        private const val BUDGET_MS = 5 * 60 * 60 * 1000L

        @Volatile
        private var running = false

        private fun usedToday(context: Context): Long {
            val p = context.getSharedPreferences(PREFS, 0)
            return if (p.getLong("day", -1) == System.currentTimeMillis() / DAY_MS) p.getLong("used", 0) else 0
        }

        /** Starts the service if it isn't already keeping analysis alive (and has background time left today). */
        fun start(context: Context) {
            if (running || usedToday(context) >= BUDGET_MS) return
            val intent = Intent(context, AnalysisService::class.java)
            // Android only lets an app start this from the foreground; from the background,
            // analysis still runs as long as the app does.
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.w("AutoDJ", "couldn't keep analysis running in the background: ${it.message}") }
        }
    }
}
