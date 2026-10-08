package com.aravind.spectra.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.aravind.spectra.MainActivity
import com.aravind.spectra.R
import java.io.File
import kotlin.math.roundToInt

/** True while the app's screen is visible (set from MainActivity). */
object AppState {
    @Volatile
    var foreground: Boolean = false
}

object Notifier {
    const val CH_PROGRESS = "progress"
    const val CH_RESULTS = "results"
    const val CH_UPDATES = "updates"

    private const val ID_RESULT = 2001
    private const val ID_UPDATE_AVAILABLE = 3001
    private const val ID_UPDATE_READY = 3002
    private const val ACCENT = 0xFF2FBF71.toInt()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress while a track is analyzed or an update downloads"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULTS, "Analysis results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when an analysis finishes or fails while the app is closed"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New Spectra versions and downloaded updates"
            }
        )
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, n: Notification) {
        if (!canNotify(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // notification permission revoked meanwhile
        }
    }

    fun openAppIntent(context: Context): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun builder(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_spectra)
            .setColor(ACCENT)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)

    /** Only when the app isn't on screen: the in-app result is enough otherwise. */
    fun analysisDone(context: Context, track: String, summary: String) {
        if (AppState.foreground) return
        ensureChannels(context)
        val text = "$track · $summary"
        post(
            context, ID_RESULT,
            builder(context, CH_RESULTS)
                .setContentTitle("Analysis complete")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build()
        )
    }

    fun analysisFailed(context: Context, message: String) {
        if (AppState.foreground) return
        ensureChannels(context)
        post(
            context, ID_RESULT,
            builder(context, CH_RESULTS)
                .setContentTitle("Analysis failed")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .build()
        )
    }

    fun updateAvailable(context: Context, version: String) {
        ensureChannels(context)
        post(
            context, ID_UPDATE_AVAILABLE,
            builder(context, CH_UPDATES)
                .setContentTitle("Spectra v$version is available")
                .setContentText("Open Spectra to download and install it")
                .build()
        )
    }

    /** Downloaded while the app was in the background: one tap opens Android's installer. */
    fun updateReady(context: Context, version: String, file: File) {
        ensureChannels(context)
        NotificationManagerCompat.from(context).cancel(ID_UPDATE_AVAILABLE)
        val tap = if (context.packageManager.canRequestPackageInstalls()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val install = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(context, 2, install, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            openAppIntent(context)
        }
        post(
            context, ID_UPDATE_READY,
            builder(context, CH_UPDATES)
                .setContentTitle("Update ready")
                .setContentText("Tap to install Spectra v$version")
                .setContentIntent(tap)
                .build()
        )
    }

    fun scanDone(context: Context, summary: String) {
        if (AppState.foreground) return
        ensureChannels(context)
        post(
            context, ID_RESULT,
            builder(context, CH_RESULTS)
                .setContentTitle("Batch scan finished")
                .setContentText(summary)
                .build()
        )
    }

    fun updateFailed(context: Context, message: String) {
        ensureChannels(context)
        post(
            context, ID_UPDATE_READY,
            builder(context, CH_UPDATES)
                .setContentTitle("Update download failed")
                .setContentText(message)
                .build()
        )
    }
}

/**
 * One ongoing notification shared by the analysis and the update download. While anything runs
 * it is the notification of a foreground service, which keeps Android from freezing the app
 * when you switch away.
 */
object ProgressHub {
    const val NOTIF_ID = 1001

    enum class Kind { ANALYSIS, BATCH, UPDATE }

    private class Task(val id: Long, var title: String, var text: String, var progress: Float?)

    private val tasks = LinkedHashMap<Kind, Task>()
    private var nextId = 1L

    /** Returns a token to pass to [update] and [end], so a stale job can't end a newer one. */
    @Synchronized
    fun begin(context: Context, kind: Kind, title: String, text: String): Long {
        val app = context.applicationContext
        val wasIdle = tasks.isEmpty()
        val id = nextId++
        tasks[kind] = Task(id, title, text, null)
        Notifier.ensureChannels(app)
        if (wasIdle) {
            try {
                ContextCompat.startForegroundService(app, Intent(app, WorkService::class.java))
            } catch (e: Exception) {
                post(app) // the system refused a foreground service: fall back to a plain notification
            }
        } else {
            post(app)
        }
        return id
    }

    /** progress 0..1, or null when unknown. */
    @Synchronized
    fun update(context: Context, kind: Kind, id: Long, text: String, progress: Float?) {
        val t = tasks[kind]
        if (t == null || t.id != id) return
        t.text = text
        t.progress = progress
        post(context.applicationContext)
    }

    @Synchronized
    fun end(context: Context, kind: Kind, id: Long) {
        val t = tasks[kind]
        if (t == null || t.id != id) return
        tasks.remove(kind)
        val app = context.applicationContext
        if (tasks.isEmpty()) {
            app.stopService(Intent(app, WorkService::class.java))
            NotificationManagerCompat.from(app).cancel(NOTIF_ID)
        } else {
            post(app)
        }
    }

    @Synchronized
    fun isIdle(): Boolean = tasks.isEmpty()

    @Synchronized
    fun buildNotification(context: Context): Notification {
        Notifier.ensureChannels(context)
        val analysis = tasks[Kind.ANALYSIS]
        val primary = analysis ?: tasks[Kind.BATCH] ?: tasks[Kind.UPDATE]
        val other = if (primary != null && primary !== tasks[Kind.UPDATE]) tasks[Kind.UPDATE] else null

        val b = NotificationCompat.Builder(context, Notifier.CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_spectra)
            .setColor(0xFF2FBF71.toInt())
            .setContentIntent(Notifier.openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (primary == null) {
            b.setContentTitle("Spectra").setContentText("Working…").setProgress(0, 0, true)
        } else {
            b.setContentTitle(primary.title).setContentText(primary.text)
            val p = primary.progress
            if (p == null) b.setProgress(0, 0, true)
            else b.setProgress(100, (p * 100).roundToInt().coerceIn(0, 100), false)
            if (other != null) b.setSubText("${other.title}: ${other.text}")
        }
        return b.build()
    }

    private fun post(app: Context) {
        Notifier.post(app, NOTIF_ID, buildNotification(app))
    }
}
