package com.aravind.spectra.notify

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat

/** Does no work itself: it only keeps the app alive (and the progress notification pinned) while a job runs. */
class WorkService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            ProgressHub.NOTIF_ID,
            ProgressHub.buildNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (ProgressHub.isIdle()) stopSelf() // the job already finished
        return START_NOT_STICKY
    }
}
