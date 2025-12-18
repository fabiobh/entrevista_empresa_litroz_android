package com.fabiocunha.todoapplitroz.data.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.fabiocunha.todoapplitroz.data.local.LocalTaskDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes app lifecycle and schedules sync when app goes to background with pending changes
 */
@Singleton
class AppLifecycleSyncObserver @Inject constructor(
    private val localDataSource: LocalTaskDataSource,
    private val syncScheduler: SyncScheduler
) : DefaultLifecycleObserver {
    
    private lateinit var scope: CoroutineScope
    
    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        
        // App is going to background, check for pending changes and schedule sync
        scope.launch {
            val pendingTasksResult = localDataSource.getPendingSyncTasks()
            if (pendingTasksResult.isSuccess) {
                val pendingTasks = pendingTasksResult.getOrThrow()
                if (pendingTasks.isNotEmpty()) {
                    // There are pending changes, schedule immediate sync
                    syncScheduler.scheduleImmediateSync()
                }
            }
        }
    }
}