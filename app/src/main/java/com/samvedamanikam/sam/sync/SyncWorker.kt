package com.samvedamanikam.sam.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.samvedamanikam.sam.SamApp
import java.util.concurrent.TimeUnit

/**
 * Sends the outbox, then refreshes the dashboard (which updates the widget).
 * Runs right after a change ("sam-sync") and every 30 minutes ("sam-refresh"), only with a network.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SamApp).container
        if (!container.deviceAuth.paired.value) return Result.success()

        val repo = container.repository()
        val flushed = OutboxSync.flush(container.outbox, repo)
        runCatching { repo.dashboard() }
        container.refreshWidgets()
        return if (flushed) Result.success() else Result.retry()
    }

    companion object {
        private const val SYNC = "sam-sync"
        private const val REFRESH = "sam-refresh"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Send pending changes as soon as there's a network. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(SYNC, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** Keeps the widget fresh (and the server warm) while the app is closed. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(REFRESH, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelPending(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC)
        }
    }
}
