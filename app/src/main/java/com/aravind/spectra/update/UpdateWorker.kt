package com.aravind.spectra.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aravind.spectra.notify.AppState
import com.aravind.spectra.notify.Notifier
import java.util.concurrent.TimeUnit

/** Once a day: checks GitHub for a newer release and posts a notification (once per version). */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (AppState.foreground) return Result.success() // the app's own popup covers this

        val app = applicationContext
        val info = try {
            UpdateChecker.fetchLatest()
        } catch (e: Exception) {
            return Result.retry()
        }
        if (info == null) return Result.success()

        val prefs = app.getSharedPreferences("spectra_update", Context.MODE_PRIVATE)
        if (UpdateChecker.isNewer(info.version, UpdateChecker.currentVersion(app)) &&
            prefs.getString("skipped_version", null) != info.version &&
            prefs.getString("notified_version", null) != info.version
        ) {
            Notifier.updateAvailable(app, info.version)
            prefs.edit().putString("notified_version", info.version).apply()
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "spectra-update-check",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
