package com.fabiocunha.todoapplitroz.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fabiocunha.todoapplitroz.domain.usecase.SyncTasksUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that handles background synchronization of tasks
 * Implements proper retry policy and error handling for sync operations
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncTasksUseCase: SyncTasksUseCase
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        const val WORK_NAME = "sync_tasks_work"
        const val TAG_SYNC = "sync"
    }
    
    override suspend fun doWork(): Result {
        return try {
            val syncResult = syncTasksUseCase()
            
            if (syncResult.isSuccess) {
                Result.success()
            } else {
                val exception = syncResult.exceptionOrNull()
                
                // Determine if we should retry based on the exception type
                when {
                    // Network-related errors should be retried
                    exception?.message?.contains("network", ignoreCase = true) == true ||
                    exception?.message?.contains("connection", ignoreCase = true) == true ||
                    exception?.message?.contains("timeout", ignoreCase = true) == true -> {
                        Result.retry()
                    }
                    
                    // Server errors (5xx) should be retried
                    exception?.message?.contains("server error", ignoreCase = true) == true ||
                    exception?.message?.contains("5", ignoreCase = true) == true -> {
                        Result.retry()
                    }
                    
                    // Client errors (4xx) should not be retried
                    exception?.message?.contains("client error", ignoreCase = true) == true ||
                    exception?.message?.contains("4", ignoreCase = true) == true -> {
                        Result.failure()
                    }
                    
                    // Offline errors should be retried when network becomes available
                    exception?.message?.contains("offline", ignoreCase = true) == true -> {
                        Result.retry()
                    }
                    
                    // Default to retry for unknown errors
                    else -> Result.retry()
                }
            }
        } catch (e: Exception) {
            // Log the exception for debugging
            android.util.Log.e("SyncWorker", "Sync failed with exception", e)
            
            // Retry on unexpected exceptions
            Result.retry()
        }
    }
}