package com.example.livora.data.usage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.livora.data.db.AppDatabase
import java.util.concurrent.TimeUnit

class UsageSnapshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!UsageAccess.isGranted(applicationContext)) return Result.success()
        return try {
            UsageRepository(applicationContext, AppDatabase.get(applicationContext)).refreshRecent()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "usage-snapshot"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSnapshotWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
