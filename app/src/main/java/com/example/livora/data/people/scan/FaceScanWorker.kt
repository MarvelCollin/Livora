package com.example.livora.data.people.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.livora.R
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.media.MediaAccess
import java.text.NumberFormat

class FaceScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(applicationContext, ScanStatus.progress.value)

    override suspend fun doWork(): Result {
        val services = PeopleServices.get(applicationContext)
        if (!MediaAccess.hasAnyAccess(applicationContext)) {
            ScanStatus.publish(ScanProgress(ScanPhase.Failed, message = "No photo access"))
            return Result.failure()
        }
        val outcome = services.scanner.exclusive { runScan(services) }
        return outcome ?: Result.success()
    }

    private suspend fun runScan(services: PeopleServices): Result {
        return try {
            val plan = services.scanner.prepare()
            if (plan.isEmpty && !services.prefs.groupingPending) {
                com.example.livora.data.people.media.MediaChange.remember(applicationContext, services.prefs, plan.generation, plan.mediaCount)
                services.prefs.initialScanDone = true
                if (com.example.livora.BuildConfig.DEBUG) Diagnostics.run(services.database)
                ScanStatus.publish(ScanProgress(ScanPhase.Done, plan.eligibleTotal, plan.eligibleTotal))
                return Result.success()
            }
            if (plan.pending.size >= FOREGROUND_MIN_PHOTOS) {
                try {
                    setForeground(foregroundInfo(applicationContext, ScanStatus.progress.value))
                } catch (e: Exception) {
                    android.util.Log.w(GalleryScannerTag, "foreground not started")
                }
            }
            val result = services.scanner.execute(
                plan,
                onProgress = { notify(applicationContext, it) },
                isStopped = { isStopped }
            )
            services.scanner.finish(plan, result)
            if (result.completed) Result.success() else Result.retry()
        } catch (e: Exception) {
            android.util.Log.e(GalleryScannerTag, "scan failed", e)
            ScanStatus.publish(ScanProgress(ScanPhase.Failed, message = "Scan stopped by an error"))
            Result.retry()
        }
    }

    companion object {
        private const val GalleryScannerTag = "PeopleScan"
        const val CHANNEL_ID = "people_scan"
        const val NOTIFICATION_ID = 4201
        const val FOREGROUND_MIN_PHOTOS = 40

        fun foregroundInfo(context: Context, progress: ScanProgress): ForegroundInfo {
            ensureChannel(context)
            val notification = build(context, progress)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        }

        fun notify(context: Context, progress: ScanProgress) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, build(context, progress))
            } catch (e: SecurityException) {
                return
            }
        }

        private fun build(context: Context, progress: ScanProgress): android.app.Notification {
            val format = NumberFormat.getIntegerInstance()
            val text = if (progress.total > 0) {
                "Scanned ${format.format(progress.scanned)} of ${format.format(progress.total)} photos"
            } else {
                "Preparing"
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_people)
                .setContentTitle("Finding people in your photos")
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true)
                .setProgress(progress.total.coerceAtLeast(1), progress.scanned, progress.total <= 0)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build()
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(CHANNEL_ID, "People scan", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Progress while Livora finds people in your photos"
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
    }
}
