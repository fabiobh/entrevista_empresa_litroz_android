package com.fabiocunha.todoapplitroz.data.sync

import androidx.work.*
import com.fabiocunha.todoapplitroz.data.worker.SyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scheduler for background synchronization tasks using WorkManager
 * Handles scheduling sync work with proper constraints and retry policies
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    
    companion object {
        private const val SYNC_WORK_NAME = "sync_tasks_work"
        private const val IMMEDIATE_SYNC_WORK_NAME = "immediate_sync_work"
    }
    
    /**
     * Schedules periodic background sync with network constraints
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15, // Minimum interval for periodic work
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(SyncWorker.TAG_SYNC)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )
    }
    
    /**
     * Schedules immediate sync when app goes to background with pending changes
     */
    fun scheduleImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(SyncWorker.TAG_SYNC)
            .build()
        
        workManager.enqueueUniqueWork(
            IMMEDIATE_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }
    
    /**
     * Schedules sync when connectivity is restored
     */
    fun scheduleConnectivitySync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(SyncWorker.TAG_SYNC)
            .build()
        
        workManager.enqueueUniqueWork(
            "connectivity_sync_work",
            ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }
    
    /**
     * Cancels all sync work
     */
    fun cancelAllSyncWork() {
        workManager.cancelAllWorkByTag(SyncWorker.TAG_SYNC)
    }
    
    /**
     * Gets the status of sync work
     */
    fun getSyncWorkStatus() = workManager.getWorkInfosByTagLiveData(SyncWorker.TAG_SYNC)
}