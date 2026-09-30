package com.example.livora.data.people.scan

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object ScanController {

    private const val UNIQUE_NAME = "people_scan"
    private const val PERIODIC_NAME = "people_scan_periodic"
    const val KEY_BUCKETS = "buckets"

    fun start(context: Context) {
        val request = OneTimeWorkRequestBuilder<FaceScanWorker>()
            .addTag(UNIQUE_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun startFolders(context: Context, bucketIds: Set<Long>) {
        if (bucketIds.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<FaceScanWorker>()
            .setInputData(Data.Builder().putLongArray(KEY_BUCKETS, bucketIds.toLongArray()).build())
            .addTag(UNIQUE_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}
