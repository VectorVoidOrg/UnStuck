package com.ufo.fraudobserver.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ufo.fraudobserver.data.repository.FraudRepository
import java.util.concurrent.TimeUnit

/**
 * Module D: Delta Sync Worker
 * Periodically fetches newly blacklisted VPAs from central registry.
 */
class BlacklistSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BlacklistSyncWorker"
        private const val WORK_NAME = "ufo_blacklist_delta_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<BlacklistSyncWorker>(
                6, TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.d(TAG, "Scheduled BlacklistSyncWorker (every 6 hours).")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val repository = FraudRepository(applicationContext)
            val count = repository.syncBlacklistDelta()
            Log.d(TAG, "Blacklist delta sync completed. Updated: $count VPAs")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error running BlacklistSyncWorker", e)
            Result.retry()
        }
    }
}

