package com.fabiocunha.todoapplitroz.data.sync

import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes connectivity changes and triggers sync when network becomes available
 */
@Singleton
class ConnectivitySyncObserver @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val syncScheduler: SyncScheduler
) {
    
    private var wasOffline = false
    
    /**
     * Starts observing connectivity changes and schedules sync when coming online
     */
    fun startObserving(scope: CoroutineScope) {
        scope.launch {
            networkMonitor.isOnline
                .distinctUntilChanged()
                .collect { isOnline ->
                    if (isOnline && wasOffline) {
                        // Network became available after being offline
                        syncScheduler.scheduleConnectivitySync()
                    }
                    wasOffline = !isOnline
                }
        }
    }
}