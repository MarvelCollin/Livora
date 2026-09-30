package com.example.livora.data.people.scan

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ScanController {

    private const val UNIQUE_NAME = "people_scan"
    private const val PERIODIC_NAME = "people_scan_periodic"

    fun startIfNeeded(context: Context, prefs: com.example.livora.data.people.PeoplePrefs) {
        if (com.example.livora.data.people.media.MediaChange.hasChanged(context, prefs)) start(context)
    }

    fun start(context: Context) {
        val request = OneTimeWorkRequestBuilder<FaceScanWorker>()
            .addTag(UNIQUE_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<FaceScanWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(12, TimeUnit.HOURS)
            .addTag(PERIODIC_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}
